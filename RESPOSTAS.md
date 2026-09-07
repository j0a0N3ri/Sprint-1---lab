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
