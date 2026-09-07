package br.edu.iceibank.agencia.controller.dto;

import java.math.BigDecimal;

/**
 * Corpo de POST /contas/{id}/creditar-remoto - a mensagem que uma agencia envia a outra.
 *
 * O campo timestampLamport e a peca central do algoritmo: e o "anexar o valor a mensagem"
 * da regra 2, que permite ao destinatario aplicar a regra 3 (max(local, recebido) + 1).
 * Sem carregar o timestamp no corpo da mensagem, os relogios das duas agencias nunca
 * teriam relacao causal nenhuma.
 */
public record CreditoRemotoRequest(BigDecimal valor, Integer timestampLamport, Integer origemAgencia) {}
