package br.edu.iceibank.agencia.controller.dto;

import java.math.BigDecimal;

/** Corpo de POST /transferencias */
public record TransferenciaRequest(Integer idOrigem, Integer idDestino, BigDecimal valor) {}
