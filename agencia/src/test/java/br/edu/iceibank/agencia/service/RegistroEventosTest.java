package br.edu.iceibank.agencia.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que cada evento vira exatamente uma linha JSON no .jsonl, com os campos que
 * o script de linha do tempo (Parte E) espera encontrar.
 */
class RegistroEventosTest {

    @Test
    void gravaUmaLinhaJsonPorEvento(@TempDir Path pasta) throws Exception {
        RegistroEventos registro = new RegistroEventos("agencia-teste", pasta);
        RelogioLamport relogio = new RelogioLamport();

        registro.registrar("CRIAR_CONTA", relogio.eventoLocal(), Map.of("id", 0));
        registro.registrar("DEPOSITO", relogio.eventoLocal(), Map.of("id", 0, "valor", 25));

        List<String> linhas = Files.readAllLines(pasta.resolve("eventos-agencia-teste.jsonl"));
        assertEquals(2, linhas.size());

        assertTrue(linhas.get(0).contains("\"agencia\":\"agencia-teste\""));
        assertTrue(linhas.get(0).contains("\"tipo\":\"CRIAR_CONTA\""));
        assertTrue(linhas.get(0).contains("\"timestampLamport\":1"));
        assertTrue(linhas.get(0).contains("\"horaParede\""));
        assertTrue(linhas.get(1).contains("\"timestampLamport\":2"));
    }
}
