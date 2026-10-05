package br.edu.iceibank.agencia.tools;

import org.junit.jupiter.api.Test;

import static br.edu.iceibank.agencia.tools.MesclarLogs.Relacao.ANTES;
import static br.edu.iceibank.agencia.tools.MesclarLogs.Relacao.CONCORRENTES;
import static br.edu.iceibank.agencia.tools.MesclarLogs.Relacao.DEPOIS;
import static br.edu.iceibank.agencia.tools.MesclarLogs.Relacao.IGUAIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MesclarLogsTest {

    @Test
    void comparaTodasAsRelacoesPossiveis() {
        assertEquals(ANTES, MesclarLogs.compararVetores(new int[]{3, 1, 0}, new int[]{3, 2, 0}));
        assertEquals(DEPOIS, MesclarLogs.compararVetores(new int[]{3, 2, 0}, new int[]{3, 1, 0}));
        assertEquals(CONCORRENTES,
            MesclarLogs.compararVetores(new int[]{3, 1, 0}, new int[]{1, 3, 0}));
        assertEquals(IGUAIS, MesclarLogs.compararVetores(new int[]{2, 2, 1}, new int[]{2, 2, 1}));
    }

    @Test
    void rejeitaVetoresIncompativeis() {
        assertThrows(IllegalArgumentException.class,
            () -> MesclarLogs.compararVetores(new int[]{1, 2}, new int[]{1, 2, 3}));
    }
}
