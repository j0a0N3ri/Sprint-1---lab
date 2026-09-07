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
