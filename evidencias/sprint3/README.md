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
