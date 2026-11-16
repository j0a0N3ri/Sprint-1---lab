# Validação do Sprint 3

O backend foi executado na suíte Maven, incluindo o cenário de HTTP 409 pela camada MVC
do Spring. O runner local recusou a abertura de sockets TCP, o que bloqueou a demonstração
simultânea das três agências e o emulador Android não estava disponível. A tabela descreve
as capturas a obter em uma execução com acesso à rede e ao dispositivo. O ambiente de
entrega usa `Get-Date` visível em um terminal.

| Arquivo do roteiro | Cenário |
| --- | --- |
| `eleicao-inicial.png` | Respostas de 4000, 4001 e 4002 indicando a agência 2 |
| `failover.png` | Derrubar a agência 2 e consultar 4000 e 4001, que devem indicar 1 |
| `historico-lideranca.png` | Saída de `node agencia/historico-lideranca.js` após a queda |
| `app-lider-atual.png` | Flutter conectado com a agência 2 como líder |
| `app-failover.png` | Sem interação no app, agência 1 passa a aparecer como líder |
| `conflito-sincronizacao.png` | POST com vetor antigo devolve 409, vetor e conta atualizados |
| `app-offline.png` | Modo avião com depósito listado como pendente |
| `app-sincronizacao.png` | Rede reativada, saldo confirmado e fila drenada automaticamente |
| `funcionalidade-adicional.png` | Agência 0 sem resposta e agência 2 ainda líder |

## Resultados obtidos nesta sessão

- A suíte Maven passou com 24 testes. O teste Bully usa três instâncias do serviço
  e transporte em memória. A subida escalonada convergiu de 0 para 1 e depois 2.
  O heartbeat reelegeu 1 após retirar 2. O teste Spring MVC confirmou HTTP 409
  com saldo 125, aplicação com o vetor atualizado e repetição sem duplicar o saldo 135.
- A análise estática do Flutter passou e o build web foi concluído. A lógica da fila
  também foi executada no motor Flutter com uma API simulada, incluindo reconexão,
  esvaziamento da fila e conflito visível.
- [`testes-locais/`](testes-locais/) contém capturas da interface Flutter em execução
  com API simulada, identificadas nas próprias imagens, e o resultado dos testes Java.
  A validação fim a fim com três processos de rede, RabbitMQ ativo e Android ainda
  precisa ser feita para produzir os prints exatos solicitados no roteiro.
