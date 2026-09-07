# ICEIBank — Sprint 1

Banco simplificado dividido em 3 agencias independentes, cada uma responsavel por uma particao
das contas. Toda operacao e carimbada com um **relogio logico de Lamport**, e os eventos das
tres agencias podem ser mesclados em uma unica linha do tempo ordenada por causalidade.

Projeto individual da disciplina de Laboratorio de Desenvolvimento de Aplicacoes Moveis e
Distribuidas (U2 — Desenvolvimento Web: arquitetura MVC e servicos REST).

> **Atencao:** o roteiro da disciplina traz os exemplos em Node.js/Express, mas a entrega **nao
> pode ser em Node**. Este projeto e em **Java 21 + Spring Boot** — os comandos abaixo sao
> `mvn`, nao `node`.

## Requisitos

- **JDK 21** (o `pom.xml` exige `java.version=21`)
- **Maven 3.9+**
- Um navegador, para o frontend

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

## Como executar

O **mesmo codigo** roda tres vezes, com identidades diferentes. Quem decide qual agencia o
processo e a variavel de ambiente `AGENCIA_ID`; dela saem a porta (`4000 + AGENCIA_ID`) e o
nome do arquivo de log.

```bash
cd agencia
mvn package                     # compila e roda os 16 testes

# tres terminais, um por agencia
AGENCIA_ID=0 java -jar target/agencia-0.0.1-SNAPSHOT.jar   # porta 4000
AGENCIA_ID=1 java -jar target/agencia-0.0.1-SNAPSHOT.jar   # porta 4001
AGENCIA_ID=2 java -jar target/agencia-0.0.1-SNAPSHOT.jar   # porta 4002
```

Frontend: abra `frontend/index.html` no navegador (duplo clique basta — nao ha build).

Usuarios de demonstracao: `ana`, `bruno`, `carla`, `davi` — senha `senha123` para todos.

## Particionamento

A agencia responsavel por uma conta e `id_conta % 3`. Cada agencia **recusa** operar contas que
nao sao dela.

| Agencia | Porta | Contas |
|---|---|---|
| 0 | 4000 | 0, 3, 6, 9, ... |
| 1 | 4001 | 1, 4, 7, 10, ... |
| 2 | 4002 | 2, 5, 8, 11, ... |

## API

Todas as rotas exigem `Authorization: Bearer <token>`, exceto o login.

| Metodo | Rota | Quem pode |
|---|---|---|
| POST | `/auth/login` | publico |
| GET | `/contas` | usuario (lista so as contas dele) |
| POST | `/contas` | usuario (vira dono da conta criada) |
| GET | `/contas/{id}` | dono da conta |
| POST | `/contas/{id}/depositar` | dono da conta |
| POST | `/contas/{id}/sacar` | dono da conta |
| POST | `/transferencias` | dono da conta de **origem** |
| POST | `/contas/{id}/creditar-remoto` | **apenas outra agencia** (token de servico) |
| GET | `/limites` | usuario |

## Linha do tempo unificada

Le os `.jsonl` das tres agencias e monta uma unica linha do tempo ordenada pelo relogio de
Lamport, apontando empates entre agencias (eventos concorrentes) e divergencias em relacao a
hora de parede:

```bash
cd agencia
mvn -q exec:java
```

## Estrutura

```
iceibank/
├── agencia/                                   servico da agencia (Spring Boot)
│   └── src/main/java/br/edu/iceibank/agencia/
│       ├── config/      AgenciaConfig (particao), SecurityConfig, LimitesConfig
│       ├── controller/  camada C do MVC — so traduz HTTP
│       ├── model/       camada M — Conta
│       ├── security/    JWT: emissao, filtro, autorizacao
│       ├── service/     regras: BancoService, TransferenciaService, RelogioLamport, RegistroEventos
│       └── tools/       MesclarLogs — linha do tempo unificada
├── frontend/                                  HTML/CSS/JS puro, em MVC (model/view/controller)
├── evidencias/sprint1/                        prints de teste
├── RESPOSTAS.md                               respostas das questoes e decisoes de design
└── README.md
```

Os logs em `agencia/data/*.jsonl` sao gerados em tempo de execucao e **nao** sao versionados.
As contas ficam em memoria: reiniciar uma agencia zera as contas dela — comportamento esperado
neste sprint, que nao tem banco de dados.

## Limitacao conhecida (intencional)

Se uma transferencia entre agencias falhar depois do debito (agencia de destino fora do ar), o
debito **nao** e revertido: o dinheiro some temporariamente. O sistema registra a inconsistencia
no log (`TRANSFERENCIA_FALHOU`, com `debitoRevertido: false`) em vez de escode-la. Resolver isso
com atomicidade real e o tema do **Sprint 4** (2PC/Saga) — a justificativa de por que nao basta
estornar no `catch` esta em `RESPOSTAS.md`, Parte D.
