# ICEIBank — Sprint 1: respostas e decisoes de projeto

Aluno: Joao Vitor Neri Moreira
Disciplina: Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas — U2
Linguagem escolhida: **Java 21 + Spring Boot** (mantida do Sprint 1 ao Sprint 4)
Frontend: HTML + CSS + JavaScript puro (sem framework)

## Declaracao de uso de IA

Utilizei ferramenta de IA como apoio pontual em revisao de codigo e redacao deste
documento, nos termos permitidos pelo enunciado. As decisoes de arquitetura e o
codigo entregue sao de minha autoria e posso explicar e defender qualquer trecho.

---

## Parte B — Relogio de Lamport (secao 6.4)

**1. Por que o relogio usa `max(contador_local, timestampRecebido) + 1` ao receber uma
mensagem, em vez de adotar o timestamp recebido diretamente?**

Porque adotar o timestamp recebido diretamente permitiria que o relogio **andasse para tras**.
Se a Agencia 0 ja processou 10 eventos locais (contador 10) e recebe uma mensagem carimbada
com 3, adotar o 3 faria os proximos eventos dela receberem 4, 5, 6... — timestamps que ela ja
tinha usado. Dois eventos diferentes com o mesmo numero na mesma agencia destroem a unica
garantia do algoritmo: se A causou B, entao `timestamp(A) < timestamp(B)`.

O `max` garante que o relogio nunca retrocede (preserva a ordem dos eventos locais anteriores),
e o `+ 1` garante que o evento de recebimento seja estritamente maior que o de envio — afinal
enviar acontece antes de receber, e a desigualdade tem que ser estrita, nao "maior ou igual".
Escrevi esse comportamento como teste em `RelogioLamportTest.relogioNuncaAndaParaTras()`:
alimentando o relogio com timestamps fora de ordem (5, 1, 2, 40, 3, 41), o contador resultante
so cresce.

**2. Se a Agencia 0 esta no contador 10 e recebe uma mensagem com timestamp 3, qual o novo
valor? O que isso implica sobre agencias rapidas e agencias lentas?**

O novo valor e **11**: `max(10, 3) + 1`. A mensagem atrasada e simplesmente ignorada para
efeito de valor — quem manda e o relogio local, que ja estava mais adiantado.

A implicacao e que **o relogio de Lamport nao mede tempo, mede quantidade de eventos com
relacao causal**. Uma agencia que processa muitas operacoes sobe o contador rapido; uma agencia
ociosa fica com contador baixo. Quando a lenta manda uma mensagem para a rapida, o timestamp
dela e engolido pelo `max` e nao muda nada. Mas o contrario e visivel: quando a **rapida** manda
para a **lenta**, a lenta pula de uma vez para `contador_da_rapida + 1`. Ou seja, o contato
entre processos so puxa relogio para cima, nunca para baixo, e sempre no sentido de quem esta
mais adiantado. Isso e o que faz numeros de agencias diferentes serem comparaveis: eles so se
sincronizam quando ha **troca real de mensagem**, que e exatamente onde existe causalidade.
Entre eventos sem troca de mensagem, os numeros de duas agencias nao querem dizer nada um sobre
o outro — e por isso que a linha do tempo da Parte E mostra empates entre agencias diferentes.

---

## Parte D — Transferencias (secao 8.3)

**1. Por que a transferencia local nao precisa de `aoEnviar()`/`aoReceber()`, enquanto a
transferencia entre agencias precisa?**

Porque o relogio de Lamport so precisa das regras 2 e 3 quando ha **troca de mensagem entre
processos diferentes**. Na transferencia local, debito e credito acontecem dentro do mesmo
processo, na mesma agencia, sob o mesmo lock: sao dois eventos locais comuns, e a ordem entre
eles ja esta garantida pelo proprio incremento sequencial do contador (regra 1). Nao existe
mensagem atravessando a rede, entao nao ha nada para carimbar nem para receber.

Isso e visivel no log da Agencia 0. Na transferencia local, os dois eventos sao consecutivos
no mesmo arquivo:

```
TRANSFERENCIA_DEBITO   timestampLamport 3   (0 -> 3, valor 40)
TRANSFERENCIA_CREDITO  timestampLamport 4   (0 -> 3, valor 40)
```

Ja na transferencia entre agencias, os dois eventos estao em **arquivos diferentes**, gerados
por processos que nao compartilham memoria nem contador:

```
agencia-0:  TRANSFERENCIA_DEBITO           timestampLamport 5
            (envio da mensagem carimbou 6, via aoEnviar)
agencia-1:  TRANSFERENCIA_CREDITO_REMOTO   timestampLamport 7   timestampRecebido 6
```

A Agencia 1 estava no contador 1 e pulou para 7, porque `max(1, 6) + 1 = 7`. Sem a regra 3, ela
teria registrado o credito com timestamp 2, **menor** que o 5 do debito que o causou — e a linha
do tempo da Parte E mostraria o dinheiro chegando antes de sair.

**2. Reproduza a falha conhecida e observe o saldo da origem depois do erro. Ele foi revertido?
O que isso significa em termos de consistencia?**

Nao foi revertido. Derrubei a Agencia 1 (`kill -9` na porta 4001) e transferi 25 da conta 0 para
a conta 1. A API respondeu **502**:

```
{"erro":"Falha ao contatar a agencia de destino. Debito ja aplicado - inconsistencia conhecida (ver Sprint 4)."}
```

E o saldo da conta 0 caiu de 130 para **105** — os 25 sairam da origem e nunca chegaram ao
destino. O log da Agencia 0 registra a inconsistencia em vez de escode-la:

```
TRANSFERENCIA_DEBITO   timestampLamport 7   valor 25   saldoOrigem 105
TRANSFERENCIA_FALHOU   timestampLamport 9   valor 25   erro "Connection refused"   debitoRevertido false
```

Em termos de consistencia: o sistema perdeu a **atomicidade**. A transferencia deveria ser tudo
ou nada, e ficou pela metade — 25 reais deixaram de existir no somatorio do banco. Nenhuma
invariante local foi violada (a conta 0 nao ficou negativa, a Agencia 1 nao gravou nada errado),
mas a invariante **global** — a soma dos saldos das tres agencias e constante em qualquer
transferencia — quebrou. E o tipo de erro que nao aparece olhando uma agencia por vez.

Vale registrar por que o codigo nao simplesmente estorna no `catch`: **um timeout nao diz se a
operacao aconteceu**. Se a Agencia 1 tivesse recebido a mensagem, creditado a conta e caido
antes de responder, o estorno criaria dinheiro do nada — a conta 1 com o credito e a conta 0
com o valor de volta. Estornar as cegas troca "dinheiro sumiu" por "dinheiro duplicado", que e
pior. Por isso o sistema registra e para.

**3. Duas formas possiveis de corrigir isso no Sprint 4 (alto nivel).**

*a) Two-Phase Commit (2PC).* Um coordenador conduz a transferencia em duas fases. Na fase de
preparo, ele pergunta as duas agencias se conseguem executar sua parte e elas **reservam** o
valor sem efetiva-lo, respondendo "pronta" ou "abortar". So se todas responderem "pronta" ele
manda commit na segunda fase. Se qualquer uma falhar no preparo, todas recebem abort e a
reserva e liberada — nenhum saldo mudou de verdade. Ganha-se atomicidade real; o custo e que
as contas ficam bloqueadas durante o protocolo e que a queda do coordenador entre as fases
deixa os participantes travados esperando (o problema classico de bloqueio do 2PC).

*b) Saga com compensacao.* Em vez de bloquear, cada etapa e efetivada de imediato e cada uma
tem uma **operacao compensatoria** que desfaz seu efeito. A transferencia vira: debita na
origem; tenta creditar no destino; se o credito falhar em definitivo, executa a compensacao
"estorna o debito na origem". A diferenca para o estorno ingenuo do `catch` atual e que a saga
mantem estado persistente da transferencia e **reexecuta ate obter certeza**: ela reenvia o
credito enquanto nao souber o resultado, e so compensa quando confirmar que ele nao ocorreu.
Isso exige que o credito seja **idempotente** — identificado por um id unico de operacao, para
que o reenvio nao credite duas vezes. Nao ha bloqueio e o sistema tolera melhor a queda de um
no, mas existe uma janela em que o saldo esta visivelmente inconsistente (consistencia
eventual), o que num banco precisa ser uma decisao consciente, nao um acidente.

---

## Parte E — Linha do tempo unificada (secao 10.3)

### O que observei (tarefa, passo 3)

Rodei `mvn -q exec:java` depois de gerar operacoes quase simultaneas nas tres agencias. A
ferramenta encontrou **4 timestamps empatados entre agencias diferentes**. O par mais
interessante e o do **Lamport 6**:

```
Lamport 6:
  agencia-0  TRANSFERENCIA_CREDITO_REMOTO  hora de parede: 14:58:08.222747Z
  agencia-1  TRANSFERENCIA_CREDITO_REMOTO  hora de parede: 14:58:08.173053Z
```

Esses dois eventos **nao sao causalmente relacionados** — sao concorrentes. Cada um e a ponta
receptora de uma transferencia diferente: o da agencia-0 veio de uma mensagem da agencia-2
(conta 2 -> conta 3), e o da agencia-1 veio de uma mensagem da agencia-0 (conta 0 -> conta 1).
Nenhum dos dois influenciou o outro; nenhuma mensagem foi trocada entre eles. Cada um tem uma
cadeia causal propria, e as duas cadeias so por coincidencia chegaram ao numero 6:

```
agencia-2 debito (ts 3) -> envio (ts 4) -> agencia-0 recebe: max(5, 4) + 1 = 6
agencia-0 debito (ts 4) -> envio (ts 5) -> agencia-1 recebe: max(2, 5) + 1 = 6
```

O empate e o algoritmo funcionando como deveria: Lamport nao ordena eventos concorrentes
**porque nao existe ordem entre eles**. Inventar uma seria mentira.

**A ordem por hora de parede bate com a ordem por Lamport?** Nao. A ferramenta apontou **9
posicoes de divergencia** entre as duas ordenacoes. O caso mais claro:

```
agencia-1  DEPOSITO  Lamport 2  hora de parede 14:58:08.114267Z
agencia-0  DEPOSITO  Lamport 3  hora de parede 14:58:08.111877Z
```

O deposito da agencia-0 aconteceu **2,4 milissegundos antes** no relogio fisico, mas tem
timestamp de Lamport **maior**. As duas ordens discordam — e nenhuma das duas esta "errada",
porque os eventos sao concorrentes: nao ha fato do mundo que diga qual veio primeiro em termos
causais. O detalhe agravante e que isso aconteceu na **mesma maquina**, com o mesmo relogio
fisico. Em maquinas diferentes, com relogios derivando entre si, a hora de parede seria ainda
menos confiavel — e por isso ela existe no log so como campo de comparacao, e nenhuma decisao
do sistema a consulta.

### Perguntas

**1. Lamport garante que, se A causou B, entao timestamp(A) < timestamp(B), mas nao garante a
volta. O que isso significa na pratica ao ver dois eventos com timestamps diferentes?**

Significa que a implicacao so vale numa direcao, e nao dá para lê-la de tras para frente. Ver
`timestamp(A) < timestamp(B)` **nao** permite concluir que A influenciou B. Sao possiveis dois
mundos, indistinguiveis pelo numero:

- A realmente causou B (houve uma cadeia de eventos ou mensagens ligando um ao outro);
- A e B sao concorrentes e o numero menor de A e coincidencia de quantos eventos cada processo
  processou antes.

Na minha linha do tempo isso e literal: `agencia-1 DEPOSITO` (ts 2) e `agencia-0 DEPOSITO`
(ts 3) estao ordenados, mas um nao teve nada a ver com o outro — o de ts 3 ate aconteceu antes
no relogio fisico. Ou seja: a ordem que o log exibe e uma **linearizacao possivel**, nao a
historia real. Ela nunca contradiz a causalidade (essa garantia vale), mas acrescenta ordem
onde nao existia.

Na pratica, o que se pode afirmar com seguranca e a **contrapositiva**: se
`timestamp(A) >= timestamp(B)`, entao A com certeza **nao** causou B. Isso e util — serve para
descartar hipoteses de causalidade — mas e bem menos do que se costuma supor olhando a lista
ordenada.

**2. O relogio de Lamport, sozinho, bastaria para distinguir com certeza "A e B sao concorrentes"
de "A aconteceu antes de B"? Por que isso motiva o relogio vetorial do Sprint 2?**

Nao bastaria. Um empate (`timestamp(A) == timestamp(B)`) prova concorrencia, mas e o unico caso
em que o contador entrega essa informacao — e ele e raro. Nos meus dados, os dois depositos
concorrentes sairam com 2 e 3: numeros diferentes, sugerindo ordem, sendo concorrentes. Um
unico inteiro por processo **perde informacao** por construcao: ele resume "tudo o que eu ja
vi" em um numero so, e nao ha como recuperar de qual processo veio cada parte desse
conhecimento.

O relogio vetorial resolve exatamente isso guardando um contador **por processo** — cada
agencia carregaria algo como `[4, 2, 7]`, sabendo quantos eventos ja viu de cada uma das
outras. Com isso a comparacao vira elemento a elemento: `A -> B` se todo componente de A for
menor ou igual ao de B e pelo menos um for estritamente menor; e se cada vetor tiver um
componente maior que o do outro, os eventos sao **provadamente concorrentes**. A pergunta
"A e B sao concorrentes?" passa a ter resposta definitiva, e nao apenas quando ha empate.

O custo e o tamanho: em vez de um inteiro, cada mensagem e cada evento carregam N inteiros,
onde N e o numero de processos. Para 3 agencias e barato; para milhares de nos, nao. Essa troca
— precisao causal por tamanho de metadado — e a decisao que o Sprint 2 vai materializar.

---

## Parte F — Autenticacao JWT (secao 11.3)

### Decisoes de design (justificativas pedidas na secao 11.1)

**Formato das credenciais: login e senha, nao "numero da conta + senha".**
Uma pessoa pode ser dona de mais de uma conta — inclusive em agencias diferentes, ja que a
particao e por numero de conta e nao por dono. Amarrar a identidade ao numero da conta
obrigaria a um login por conta e quebraria assim que alguem tivesse duas. Com login proprio, a
identidade e da pessoa e as contas sao um atributo dela: o vinculo acontece na criacao da conta
(quem cria vira dono), e e esse vinculo que sustenta a autorizacao.

As senhas ficam guardadas com hash **BCrypt**, nunca em texto puro. Se a memoria do processo
vazar, o atacante nao ganha as senhas de graca. E o login responde a mesma mensagem
("Credenciais invalidas.") tanto para usuario inexistente quanto para senha errada — distinguir
os dois casos entregaria de graca a lista de logins validos do sistema.

**Expiracao: 15 minutos (900s), configuravel.**
Um JWT nao pode ser revogado antes de vencer — o servidor nao consulta nada para valida-lo, e e
justamente essa a vantagem dele. Logo, a expiracao e o unico limite real da janela de uso de um
token vazado. 15 minutos e o equilibrio entre seguranca e nao obrigar a pessoa a relogar toda
hora. O valor sai da propriedade `iceibank.jwt.expiracao-segundos`, o que permitiu testar o
cenario de token expirado com 5 segundos, sem esperar 15 minutos.

**Algoritmo: HMAC-SHA256 (simetrico).**
A mesma chave assina e verifica. Foi a escolha certa aqui porque as tres agencias sao do mesmo
dono, ja compartilham configuracao e qualquer uma precisa validar o token emitido por outra. Se
as agencias fossem de organizacoes diferentes, o correto seria um algoritmo assimetrico (RS256):
cada emissor guarda a chave privada e distribui apenas a publica, e ninguem consegue forjar
token em nome de outro. A agencia se recusa a subir se o segredo tiver menos de 32 caracteres —
HMAC-SHA256 exige 256 bits, e falhar na subida e melhor do que descobrir isso no primeiro login.

**A chamada entre agencias (`creditar-remoto`) carrega token — mas um token diferente.**
Essa era a decisao a justificar, e considerei tres caminhos:

1. *Deixar a rota aberta.* Descartado: seria o maior buraco do sistema. Qualquer um com acesso a
   rede poderia creditar qualquer conta em qualquer valor, sem autenticacao nenhuma — dinheiro
   de graca via `curl`.
2. *Repassar o token do usuario que iniciou a transferencia.* Descartado por dois motivos. O
   primeiro e conceitual: do lado da agencia de destino nao existe um usuario, existe um
   processo — a pessoa nem e cliente daquela agencia, e o dono da conta creditada normalmente e
   outra pessoa. O segundo e pratico: o token do usuario pode vencer no meio da operacao, e uma
   transferencia falharia por expiracao de credencial de terceiro.
3. *Token de servico proprio* — o que implementei. A agencia de origem emite um token com claim
   `tipo: SERVICO`, assinado com a mesma chave, valido por **60 segundos**: ele nasce, atravessa
   uma unica chamada e morre. A rota `/contas/{id}/creditar-remoto` exige `hasRole("SERVICO")`, e
   todas as outras exigem `hasRole("USUARIO")`.

O efeito e que as duas identidades ficam separadas de verdade, e isso e verificavel:

```
creditar-remoto SEM token                      -> 401
creditar-remoto com token de USUARIO valido    -> 403  (autenticado, mas sem permissao)
transferencia entre agencias (token de servico) -> 200
```

Um usuario legitimo nao consegue chamar a rota interna na mao para creditar a propria conta.

### Perguntas

**1. Qual a diferenca entre autenticacao e autorizacao? Sua implementacao verifica so uma das
duas? Um usuario autenticado consegue sacar de uma conta que nao e dele?**

**Autenticacao** responde "quem e voce" — e a validacao da assinatura do JWT, feita pelo
`JwtFiltro`, que coloca a identidade no contexto de seguranca. **Autorizacao** responde "voce
pode fazer isso" — e ela nao decorre da primeira: saber quem alguem e nao diz nada sobre o que
essa pessoa tem direito de fazer.

Implementei **as duas**, em duas camadas distintas:

- Autorizacao por *tipo de identidade*, no `SecurityConfig`: rota interna so aceita SERVICO,
  rotas de conta so aceitam USUARIO.
- Autorizacao por *propriedade do recurso*, no `ContextoDeSeguranca`: cada operacao sobre uma
  conta chama `exigirDonoDaConta(id)` antes de tocar no saldo.

Respondendo diretamente: **nao, um usuario autenticado nao consegue sacar de conta alheia**.
Testado — bruno, com token perfeitamente valido, tentando sacar da conta da ana:

```
POST /contas/0/sacar  Authorization: Bearer <token valido do bruno>
{"erro":"A conta 0 nao pertence ao usuario autenticado."}   [HTTP 403]
```

O 403 (e nao 401) e proposital e carrega significado: o token e valido, a pessoa esta
identificada — o que falta e permissao. Um 401 ali diria "faca login de novo", conselho inutil
para quem ja esta logado.

Uma decisao consciente: a autorizacao vale sobre a conta de **origem** da transferencia, nao
sobre a de destino. Transferir dinheiro para a conta de outra pessoa e o uso normal de um banco;
o que nao pode e *tirar* dinheiro de conta alheia.

**2. Por que o servidor nao precisa consultar um banco para validar a assinatura de um JWT? O
que isso implica sobre escalabilidade, comparado a guardar sessoes em memoria?**

Porque a validacao e um calculo, nao uma consulta. O token carrega o proprio conteudo
(`sub`, `exp`, `tipo`) e uma assinatura HMAC daquele conteudo. Para validar, o servidor
recalcula o HMAC com a chave que ja tem em memoria e compara: se bate, o conteudo nao foi
adulterado, porque nenhum atacante consegue produzir a assinatura correta sem a chave. Toda a
informacao necessaria viaja junto com a requisicao.

A implicacao pratica aparece exatamente neste projeto. Com sessao em memoria, o estado do login
moraria **dentro de um processo**: ana loga na Agencia 0, e a Agencia 1 nao faz ideia de quem
ela e. Para as tres agencias reconhecerem a mesma sessao seria preciso um armazenamento
compartilhado (um Redis, um banco) — mais uma peca de infraestrutura, mais uma consulta de rede
por requisicao, e um ponto unico de falha novo. Com JWT, basta as tres compartilharem a chave, e
elas ja compartilham. A configuracao do sistema e literalmente `SessionCreationPolicy.STATELESS`:
o servidor nao guarda nada entre requisicoes. Adicionar uma quarta agencia nao exige sincronizar
sessao com ninguem.

O custo dessa escolha e o outro lado da mesma moeda: como nada e consultado, nada pode ser
**revogado**. Um token roubado vale ate expirar, e nao ha "deslogar" de verdade — so esperar o
`exp`. Sistemas que precisam de revogacao imediata acabam reintroduzindo estado no servidor
(uma lista de tokens banidos), o que devolve parte do problema que o JWT veio resolver.

**3. O que aconteceria se a chave secreta usada para assinar o JWT vazasse?**

Seria comprometimento total do controle de acesso do sistema. Com a chave, qualquer um forja um
token valido para **qualquer** identidade, com **qualquer** validade — e as agencias nao teriam
como distinguir o forjado do legitimo, porque a unica prova que elas checam e a assinatura.
Concretamente, neste projeto o atacante poderia:

- emitir token como `ana` e esvaziar as contas dela, sem nunca saber a senha;
- emitir token com `tipo: SERVICO` e chamar `creditar-remoto` direto, creditando qualquer conta
  em qualquer valor — criando dinheiro do nada, ja que essa rota credita sem debitar ninguem;
- emitir tokens com expiracao de anos, garantindo acesso permanente.

O agravante e que **trocar as senhas nao resolveria**: a falha nao esta nas credenciais, esta na
prova de identidade. A unica reacao eficaz e trocar a chave, o que invalida de uma vez todos os
tokens em circulacao — inclusive os legitimos, derrubando todo mundo. Por isso, na configuracao,
o segredo vem de variavel de ambiente (`${JWT_SEGREDO:...}`) com um valor padrao apenas para o
ambiente local de desenvolvimento: em producao, chave em arquivo versionado no Git e um vazamento
esperando acontecer.

---

## Parte G — Frontend (secao 12.3)

### Decisoes de design (justificativas pedidas na secao 12)

**Tecnologia: HTML, CSS e JavaScript puro, sem framework.**
O requisito de plataforma era apenas "web". Escolhi sem framework por dois motivos praticos:
a pagina abre com duplo clique, sem `npm install`, sem servidor de desenvolvimento e sem etapa
de build — uma peca a menos para falhar na hora da apresentacao; e a separacao MVC fica visivel
no codigo, em tres arquivos, em vez de escondida atras das convencoes de um framework.

Uma consequencia dessa escolha: os scripts sao classicos, e nao modulos ES (`type="module"`).
Modulos ES sao bloqueados pelo navegador quando a pagina vem de `file://`, o que obrigaria a
subir um servidor HTTP so para abrir a tela. Isso tambem exigiu liberar CORS no backend, porque
uma pagina aberta por `file://` chega a API com `Origin: null`.

**Onde o token e guardado: `localStorage`.**
E o que faz a sessao sobreviver ao F5 — sem isso, recarregar a pagina exigiria novo login. A
limitacao e conhecida e vale registrar: `localStorage` e legivel por qualquer script da mesma
origem, entao um XSS rouba o token. A alternativa mais segura seria um cookie `HttpOnly`, que o
JavaScript nao enxerga, mas ai o backend precisaria emitir e ler cookie, e a API deixaria de ser
puramente stateless via cabecalho `Authorization`.

**Como o frontend aponta para as 3 agencias:** a tela de login tem um seletor de agencia de
entrada, e a agencia escolhida e guardada junto com o token. O rodape de cada opcao lembra a
particao (`contas 0, 3, 6...`), e antes de abrir uma conta o proprio frontend refaz o calculo
`id % 3` e avisa se o numero pertence a outra agencia — economiza uma requisicao e explica o
motivo, em vez de deixar a pessoa levar um 400 sem contexto.

### Perguntas

**1. Como o frontend "lembra" de reenviar o token em cada requisicao depois do login?**

Ele nao lembra em cada tela: existe **um unico ponto de saida** para a rede. Toda chamada a API
passa pela funcao `requisitar()` do `model.js`, e e la, num lugar so, que o cabecalho e anexado:

```javascript
if (estado.token) {
  opcoes.headers['Authorization'] = 'Bearer ' + estado.token;
}
```

Os metodos publicos do Model (`listarContas`, `depositar`, `sacar`, `transferir`...) sao todos
casca fina sobre essa funcao. O efeito pratico e que nenhum botao da tela sabe que existe um
token — nao ha como esquecer de mandar o cabecalho em uma tela nova, porque nenhuma tela monta
requisicao por conta propria. O token em si vem do `localStorage`, carregado na abertura da
pagina por `carregarSessaoSalva()`.

**2. Se o token expirar no meio de uma operacao, o que acontece? A interface avisa?**

Avisa, e com o motivo correto. Todo erro de API converge para a funcao `tratarErro()` do
`controller.js`, que trata o 401 de forma diferente dos demais:

```javascript
if (erro.status === 401) {
  Model.encerrarSessao();
  View.mostrarTelaLogin();
  View.mostrarMensagem('Sua sessao expirou (o token JWT tem validade limitada). Entre novamente.', 'aviso');
  return;
}
```

Fiz assim porque mostrar apenas "nao autorizado" e deixar a pessoa na tela seria pior do que
inutil: ela continuaria clicando em botoes que nunca mais funcionariam, sem entender por que.
Encerrar a sessao e voltar ao login torna o proximo passo obvio. Os outros status tem tratamento
proprio: **403** mostra a mensagem da API (a conta nao e sua), e **502** — a falha conhecida da
Parte D — alem de mostrar o erro, **recarrega os saldos**, para a pessoa ver com os proprios
olhos que o debito saiu e nao voltou.

Nenhum erro morre no console: a faixa de mensagens no topo da pagina e o unico destino de
qualquer falha, inclusive a de agencia fora do ar. Nesse caso especifico, o `fetch` rejeita com
"Failed to fetch", que nao diz nada a quem esta usando o sistema, entao o Model traduz para
"Nao foi possivel falar com a Agencia 1 (http://localhost:4001). Ela esta no ar?".

**3. Onde estao o M, o V e o C no seu frontend? Eles existem de forma clara?**

Existem, em tres arquivos com fronteiras que da para verificar lendo os `import`... ou melhor,
nesse caso, lendo o que cada arquivo **nao** faz:

| Camada | Arquivo | Responsabilidade | O que nao faz |
|---|---|---|---|
| **Model** | `js/model.js` | estado da sessao (token, usuario, agencia) e acesso a API | nunca toca no DOM |
| **View** | `js/view.js` | desenha a tela e le os campos | nunca chama `fetch` |
| **Controller** | `js/controller.js` | responde aos eventos, orquestra Model e View, trata erros | nao monta HTML nem faz requisicao |

O teste que uso para saber se a separacao e real: `model.js` nao contem nenhuma ocorrencia de
`document`, e `view.js` nao contem nenhuma de `fetch`. Se o ICEIBank ganhasse outra interface, o
Model seria reaproveitado inteiro.

Sendo honesto sobre onde o padrao fica menos puro: a comunicacao e de mao unica, do Controller
para a View. Nao ha observadores nem binding — quando um saldo muda, e o Controller que chama
`atualizarContas()` explicitamente. Num MVC classico, a View observaria o Model e se atualizaria
sozinha. Para o tamanho desta tela, achei que a indirecao de um sistema de eventos custaria mais
clareza do que traria: com sete acoes possiveis, redesenhar explicitamente e mais facil de seguir
do que rastrear quem escuta o que.

---

## Funcionalidade adicional: limite por operacao (secao 2.1)

**O que faz.** Nenhum saque pode passar de R$ 1.000,00 por operacao, e nenhuma transferencia
pode passar de R$ 5.000,00. Os dois tetos sao configuraveis por agencia
(`iceibank.limites.saque` e `iceibank.limites.transferencia`, sobrescritiveis por variavel de
ambiente), e uma rota nova, `GET /limites`, informa a regra vigente — o frontend a consulta
logo apos o login e exibe o teto na tela, para a pessoa conhecer a regra antes de tentar.

**Por que escolhi essa.** Limite por operacao e a defesa que um banco tem contra o dano de uma
credencial comprometida. Ele conversa diretamente com a Parte F: o JWT responde "quem e voce",
a autorizacao responde "esta conta e sua", e o limite responde **"mesmo sendo sua, nao tudo de
uma vez"**. As tres camadas juntas mostram que autenticacao sozinha nao e controle de risco.

**Tres decisoes de projeto que valem explicar:**

*Deposito nao tem limite.* Limite de operacao existe para conter **saida** de dinheiro; depositar
na propria conta nao causa perda a ninguem. Bancos reais seguem a mesma logica.

*O limite e checado ANTES do saldo.* Uma tentativa de sacar 20.000 de uma conta com 39.200 e
recusada por violar o limite, e nao por falta de saldo. A ordem importa porque a mensagem muda o
que a pessoa faz em seguida: "pedir aumento de limite" e um caminho, "depositar dinheiro" e
outro — e a segunda mensagem seria simplesmente falsa aqui, ja que o saldo dava.

*A recusa vai para o log de eventos, com timestamp de Lamport.* Os tipos `SAQUE_RECUSADO_LIMITE`
e `TRANSFERENCIA_RECUSADA_LIMITE` entram no mesmo `.jsonl` das demais operacoes e aparecem na
linha do tempo unificada. Uma sequencia de recusas por limite e exatamente o rastro que uma
tentativa de fraude deixaria, e ela ficaria invisivel se o sistema so respondesse 400 e
esquecesse o ocorrido.

**Evidencia de teste** (`evidencias/sprint1/funcionalidade-adicional.png`):

```
GET /limites                          -> {"limitePorSaque":1000,"limitePorTransferencia":5000,...}
deposito de 40000                     -> 200  (deposito nao tem limite)
saque de 1000    (exatamente o teto)  -> 200  (o limite e o maximo permitido, nao o primeiro proibido)
saque de 1000.01 (um centavo acima)   -> 400  "Valor acima do limite por saque (maximo 1000)."
saque de 20000   (com saldo de sobra) -> 400  recusa por LIMITE, nao por saldo
transferencia de 6000                 -> 400  "Valor acima do limite por transferencia (maximo 5000)."
saldo apos as recusas                 -> intacto: nenhuma operacao recusada mexeu no saldo
```

Cobertos tambem por 4 testes automatizados em `LimitesConfigTest`, incluindo o caso de borda que
mais erra na pratica: o valor **exatamente igual** ao limite, que deve passar.
