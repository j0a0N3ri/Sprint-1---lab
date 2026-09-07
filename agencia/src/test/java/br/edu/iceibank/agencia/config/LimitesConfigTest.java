package br.edu.iceibank.agencia.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Funcionalidade adicional: limite por operacao.
 * O caso de borda que importa e o valor EXATAMENTE igual ao limite - ele deve passar,
 * porque o limite e o maximo permitido, nao o primeiro valor proibido.
 */
class LimitesConfigTest {

    private final LimitesConfig limites =
        new LimitesConfig(new BigDecimal("1000"), new BigDecimal("5000"));

    @Test
    void valorAbaixoDoLimitePassa() {
        assertFalse(limites.saqueAcimaDoLimite(new BigDecimal("999.99")));
        assertFalse(limites.transferenciaAcimaDoLimite(new BigDecimal("4999")));
    }

    @Test
    void valorExatamenteNoLimitePassa() {
        assertFalse(limites.saqueAcimaDoLimite(new BigDecimal("1000")));
        assertFalse(limites.transferenciaAcimaDoLimite(new BigDecimal("5000")));
    }

    @Test
    void valorUmCentavoAcimaDoLimiteEhRecusado() {
        assertTrue(limites.saqueAcimaDoLimite(new BigDecimal("1000.01")));
        assertTrue(limites.transferenciaAcimaDoLimite(new BigDecimal("5000.01")));
    }

    @Test
    void limitesSaoIndependentesEntreSi() {
        // 2000 passa da transferencia mas estoura o saque: sao tetos diferentes.
        assertTrue(limites.saqueAcimaDoLimite(new BigDecimal("2000")));
        assertFalse(limites.transferenciaAcimaDoLimite(new BigDecimal("2000")));
    }
}
