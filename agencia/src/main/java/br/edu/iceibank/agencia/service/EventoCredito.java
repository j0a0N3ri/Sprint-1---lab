package br.edu.iceibank.agencia.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Contrato da mensagem publicada pela origem e consumida pela agencia de destino. */
public record EventoCredito(
    UUID idTransferencia,
    int idContaOrigem,
    int idContaDestino,
    BigDecimal valor,
    int[] vetorEnvio,
    int origemAgencia
) {}
