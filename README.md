# ICEIBank — Sprint 2

Banco simplificado dividido em tres agencias independentes. As contas sao particionadas entre
as agencias, as rotas HTTP sao protegidas por JWT e as transferencias remotas usam RabbitMQ.
Cada evento recebe um relogio vetorial, que permite distinguir causalidade de concorrencia.

Projeto individual da disciplina de Laboratorio de Desenvolvimento de Aplicacoes Moveis e
Distribuidas (U3 — Comunicacao Indireta com Publish/Subscribe).

## Requisitos

- JDK 21
- Maven 3.9+
- RabbitMQ local ou uma instancia CloudAMQP
- Navegador para o frontend

## Configuracao

A URL do RabbitMQ nao deve ser gravada no repositorio. Defina-a nos tres terminais:

```powershell
$env:RABBITMQ_URL="amqps://usuario:senha@host.cloudamqp.com/vhost"
```

Para RabbitMQ local, o valor padrao ja e `amqp://guest:guest@localhost:5672`.

## Como executar

```powershell
cd agencia
./mvnw clean package

# abra um terminal para cada processo
$env:AGENCIA_ID="0"; java -jar target/agencia-0.0.1-SNAPSHOT.jar
$env:AGENCIA_ID="1"; java -jar target/agencia-0.0.1-SNAPSHOT.jar
$env:AGENCIA_ID="2"; java -jar target/agencia-0.0.1-SNAPSHOT.jar
```

As agencias usam as portas 4000, 4001 e 4002. O frontend e HTML/CSS/JavaScript puro e nao
precisa de build.

Usuarios de demonstracao: `ana`, `bruno`, `carla` e `davi`. A senha e `senha123`.

## Particionamento

A agencia responsavel por uma conta e `id_conta % 3`.

| Agencia | Porta | Contas |
|---|---:|---|
| 0 | 4000 | 0, 3, 6, 9... |
| 1 | 4001 | 1, 4, 7, 10... |
| 2 | 4002 | 2, 5, 8, 11... |

## Mensageria

- Exchange principal: `iceibank.eventos`, tipo `topic` e duravel.
- Fila principal: `fila-agencia-<id>`, uma por agencia.
- Routing key: `agencia.<id>.creditar`.
- Mensagens publicadas como persistentes.
- Exchange de falhas: `iceibank.eventos.dlx`.
- Fila de falhas: `fila-agencia-<id>.nao-processadas`.

Quando a transferencia e remota, a origem debita a conta e publica um `EventoCredito`. A
resposta `PUBLICADA` confirma que a publicacao foi aceita; o credito acontece depois, no
consumidor da agencia de destino.

Se a agencia estiver desligada, a mensagem fica na fila. Se ela reiniciar, as contas em memoria
deixam de existir. Nesse caso, o consumidor registra `CREDITO_REMOTO_FALHOU` e envia a mensagem
para a fila de nao processadas. Essa dead-letter queue e a funcionalidade adicional da Sprint 2.

## Relogio vetorial

Cada agencia guarda um vetor com tres posicoes. Eventos locais e envios incrementam a posicao da
propria agencia. Ao receber uma mensagem, a agencia calcula o maior valor de cada posicao e
depois incrementa sua posicao.

Os logs ficam em `agencia/data/eventos-agencia-<id>.jsonl`. Para exibir a linha do tempo e os
pares comprovadamente concorrentes:

```powershell
cd agencia
./mvnw -q exec:java
```

Logs antigos da Sprint 1 usam `timestampLamport` e sao ignorados pela analise vetorial.

## API

Todas as rotas exigem `Authorization: Bearer <token>`, exceto o login.

| Metodo | Rota | Funcao |
|---|---|---|
| POST | `/auth/login` | autenticar usuario |
| GET | `/contas` | listar contas do usuario nesta agencia |
| POST | `/contas` | criar conta nesta particao |
| GET | `/contas/{id}` | consultar conta |
| POST | `/contas/{id}/depositar` | depositar |
| POST | `/contas/{id}/sacar` | sacar |
| POST | `/transferencias` | transferir localmente ou publicar credito remoto |
| GET | `/limites` | consultar limites por operacao |

A antiga rota REST `creditar-remoto` foi removida. Creditos entre agencias entram somente pelo
consumidor RabbitMQ.

## Testes

```powershell
cd agencia
./mvnw test
```

A suite cobre JWT, limites, relogio vetorial, comparacao causal, persistencia do log e o fluxo de
publicacao/consumo de creditos.

## Estrutura principal

```text
iceibank/
├── agencia/
│   └── src/main/java/br/edu/iceibank/agencia/
│       ├── config/      configuracao da agencia, seguranca e topologia RabbitMQ
│       ├── controller/  entrada HTTP
│       ├── model/       Conta
│       ├── security/    JWT e autorizacao
│       ├── service/     banco, transferencias, mensageria, consumidor e relogio vetorial
│       └── tools/       linha do tempo causal
├── frontend/            HTML, CSS e JavaScript puro
├── evidencias/
│   ├── sprint1/
│   └── sprint2/
└── RESPOSTAS.md
```

## Limitacao conhecida

Mensageria duravel impede que a mensagem desapareca quando o consumidor esta fora do ar, mas nao
persiste as contas. Reiniciar uma agencia apaga suas contas. A fila de nao processadas preserva o
evento que nao conseguiu ser aplicado, mas a consistencia financeira completa continua sendo
tema da Sprint 4.
