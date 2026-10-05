package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.LimitesConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MensageriaFluxoTest {

    @Test
    void transferenciaRemotaDebitaEPublicaEvento(@TempDir Path pasta) {
        BancoService origem = novoBanco(0, pasta.resolve("origem"));
        origem.criarConta(0, "Ana", new BigDecimal("100"));
        PublicadorCaptura mensageria = new PublicadorCaptura();
        TransferenciaService transferencias = new TransferenciaService(origem, mensageria);

        Map<String, Object> resposta = transferencias.transferir(0, 1, new BigDecimal("25"));

        assertEquals("PUBLICADA", resposta.get("status"));
        assertEquals(new BigDecimal("75"), origem.buscarConta(0).getSaldo());
        assertEquals(1, mensageria.agenciaDestino);
        assertEquals(1, mensageria.evento.idContaDestino());
        assertEquals(new BigDecimal("25"), mensageria.evento.valor());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            new int[]{3, 0, 0}, mensageria.evento.vetorEnvio());
    }

    @Test
    void consumidorAplicaCreditoECombinaOVetor(@TempDir Path pasta) {
        BancoService destino = novoBanco(1, pasta.resolve("destino"));
        destino.criarConta(1, "Bruno", new BigDecimal("50"));
        ConsumidorCreditos consumidor = new ConsumidorCreditos(destino, JsonMapper.builder().build());
        EventoCredito evento = new EventoCredito(
            java.util.UUID.randomUUID(), 0, 1, new BigDecimal("25"), new int[]{3, 0, 0}, 0);

        consumidor.consumir(JsonMapper.builder().build().writeValueAsString(evento));

        assertEquals(new BigDecimal("75"), destino.buscarConta(1).getSaldo());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
            new int[]{3, 2, 0}, destino.getRelogio().valorAtual());
    }

    @Test
    void consumidorRejeitaSemReenfileirarQuandoContaNaoExiste(@TempDir Path pasta) {
        BancoService destino = novoBanco(1, pasta.resolve("destino"));
        ConsumidorCreditos consumidor = new ConsumidorCreditos(destino, JsonMapper.builder().build());
        EventoCredito evento = new EventoCredito(
            java.util.UUID.randomUUID(), 0, 1, new BigDecimal("25"), new int[]{3, 0, 0}, 0);

        assertThrows(AmqpRejectAndDontRequeueException.class,
            () -> consumidor.consumir(JsonMapper.builder().build().writeValueAsString(evento)));
    }

    private BancoService novoBanco(int idAgencia, Path pasta) {
        return new BancoService(
            idAgencia,
            new RelogioVetorial(idAgencia, 3),
            new RegistroEventos("agencia-" + idAgencia, pasta),
            new LimitesConfig(new BigDecimal("1000"), new BigDecimal("5000"))
        );
    }

    private static class PublicadorCaptura extends MensageriaService {
        private int agenciaDestino = -1;
        private EventoCredito evento;

        PublicadorCaptura() {
            super(null, null);
        }

        @Override
        public void publicarCredito(int agenciaDestino, EventoCredito evento) {
            this.agenciaDestino = agenciaDestino;
            this.evento = evento;
        }
    }
}
