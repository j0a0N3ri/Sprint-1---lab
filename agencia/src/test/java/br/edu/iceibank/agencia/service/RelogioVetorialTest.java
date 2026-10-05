package br.edu.iceibank.agencia.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelogioVetorialTest {

    @Test
    void eventoLocalIncrementaSomenteAPosicaoDaAgencia() {
        RelogioVetorial relogio = new RelogioVetorial(1, 3);
        assertArrayEquals(new int[]{0, 1, 0}, relogio.eventoLocal());
        assertArrayEquals(new int[]{0, 2, 0}, relogio.eventoLocal());
    }

    @Test
    void aoEnviarIncrementaAntesDeCopiarOVetor() {
        RelogioVetorial relogio = new RelogioVetorial(0, 3);
        relogio.eventoLocal();
        assertArrayEquals(new int[]{2, 0, 0}, relogio.aoEnviar());
    }

    @Test
    void aoReceberFazMaximoPosicaoAPosicaoEDepoisIncrementa() {
        RelogioVetorial relogio = new RelogioVetorial(1, 3);
        relogio.eventoLocal();
        assertArrayEquals(new int[]{4, 2, 2}, relogio.aoReceber(new int[]{4, 0, 2}));
    }

    @Test
    void devolveCopiasParaProtegerOEstadoInterno() {
        RelogioVetorial relogio = new RelogioVetorial(2, 3);
        int[] copia = relogio.eventoLocal();
        copia[2] = 99;
        assertArrayEquals(new int[]{0, 0, 1}, relogio.valorAtual());
    }

    @Test
    void rejeitaVetorComTamanhoIncompativel() {
        RelogioVetorial relogio = new RelogioVetorial(0, 3);
        assertThrows(IllegalArgumentException.class, () -> relogio.aoReceber(new int[]{1, 2}));
    }
}
