package br.edu.iceibank.agencia.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testa as tres regras do relogio de Lamport isoladamente, antes de plugar na API (Parte B).
 */
class RelogioLamportTest {

    @Test
    void eventoLocalIncrementaDeUmEmUm() {
        RelogioLamport relogio = new RelogioLamport();
        assertEquals(1, relogio.eventoLocal());
        assertEquals(2, relogio.eventoLocal());
        assertEquals(3, relogio.eventoLocal());
    }

    @Test
    void aoEnviarTambemIncrementaAntesDeAnexarOTimestamp() {
        RelogioLamport relogio = new RelogioLamport();
        relogio.eventoLocal();
        assertEquals(2, relogio.aoEnviar());
    }

    @Test
    void aoReceberAdotaOMaiorEntreOLocalEORecebidoMaisUm() {
        RelogioLamport relogio = new RelogioLamport();
        // relogio local adiantado (10) recebendo mensagem atrasada (3): vence o local
        for (int i = 0; i < 10; i++) {
            relogio.eventoLocal();
        }
        assertEquals(11, relogio.aoReceber(3));

        // relogio local atrasado (11) recebendo mensagem adiantada (50): vence o recebido
        assertEquals(51, relogio.aoReceber(50));
    }

    @Test
    void relogioNuncaAndaParaTras() {
        RelogioLamport relogio = new RelogioLamport();
        int anterior = 0;
        int[] recebidos = {5, 1, 2, 40, 3, 41};
        for (int recebido : recebidos) {
            int atual = relogio.aoReceber(recebido);
            org.junit.jupiter.api.Assertions.assertTrue(atual > anterior);
            anterior = atual;
        }
    }
}
