package br.edu.iceibank.agencia.controller.dto;

import java.math.BigDecimal;

/** Corpo de POST /contas/{id}/depositar e POST /contas/{id}/sacar */
public record OperacaoRequest(BigDecimal valor) {}
