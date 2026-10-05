package br.edu.iceibank.agencia.service;

import java.util.Arrays;

/**
 * Relogio logico que preserva quanto esta agencia conhece sobre cada participante.
 * Os retornos sao copias para impedir que codigo externo altere o estado interno.
 */
public class RelogioVetorial {

    private final int idAgencia;
    private final int[] vetor;

    public RelogioVetorial(int idAgencia, int numeroAgencias) {
        if (numeroAgencias <= 0 || idAgencia < 0 || idAgencia >= numeroAgencias) {
            throw new IllegalArgumentException("Configuracao invalida do relogio vetorial.");
        }
        this.idAgencia = idAgencia;
        this.vetor = new int[numeroAgencias];
    }

    public synchronized int[] eventoLocal() {
        vetor[idAgencia]++;
        return vetor.clone();
    }

    public synchronized int[] aoEnviar() {
        vetor[idAgencia]++;
        return vetor.clone();
    }

    public synchronized int[] aoReceber(int[] vetorRecebido) {
        if (vetorRecebido == null || vetorRecebido.length != vetor.length) {
            throw new IllegalArgumentException("Vetor recebido possui tamanho invalido.");
        }
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = Math.max(vetor[i], vetorRecebido[i]);
        }
        vetor[idAgencia]++;
        return vetor.clone();
    }

    public synchronized int[] valorAtual() {
        return vetor.clone();
    }

    @Override
    public synchronized String toString() {
        return Arrays.toString(vetor);
    }
}
