package br.edu.iceibank.agencia.controller.dto;

import java.math.BigDecimal;

/** Corpo de POST /contas */
public record CriarContaRequest(Integer id, String nomeAluno, BigDecimal saldoInicial) {}
