package br.edu.iceibank.agencia.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro de eventos da agencia, um arquivo .jsonl por agencia (uma linha JSON por evento).
 * E a materia-prima da linha do tempo unificada da Parte E: sem esse arquivo nao ha como
 * observar o relogio de Lamport ordenando eventos de processos diferentes.
 *
 * Cada evento carrega dois carimbos de tempo: timestampLamport (o relogio logico, que ordena
 * o sistema) e horaParede (o relogio fisico da maquina, guardado so para comparacao na Parte E
 * - nenhuma decisao do sistema usa esse campo).
 */
public class RegistroEventos {

    private final String nomeAgencia;
    private final Path caminhoArquivo;
    private final ObjectMapper json = JsonMapper.builder().build();

    public RegistroEventos(String nomeAgencia) {
        this(nomeAgencia, Paths.get("data"));
    }

    /** Construtor com pasta explicita - usado pelos testes, para nao sujar o data/ real. */
    public RegistroEventos(String nomeAgencia, Path pastaDados) {
        this.nomeAgencia = nomeAgencia;
        try {
            Files.createDirectories(pastaDados);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel criar a pasta de dados", e);
        }
        this.caminhoArquivo = pastaDados.resolve("eventos-" + nomeAgencia + ".jsonl");
    }

    /**
     * Grava um evento no log e devolve o mapa gravado.
     *
     * O metodo e synchronized pelo mesmo motivo do RelogioLamport: o Spring Boot atende
     * requisicoes em varias threads, e duas escritas simultaneas no mesmo arquivo poderiam
     * intercalar bytes e corromper uma linha do .jsonl.
     */
    public synchronized Map<String, Object> registrar(String tipo, int timestampLamport, Map<String, Object> detalhes) {
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("agencia", nomeAgencia);
        evento.put("tipo", tipo);
        evento.put("timestampLamport", timestampLamport);
        evento.put("horaParede", Instant.now().toString());
        evento.put("detalhes", detalhes);

        try {
            String linha = json.writeValueAsString(evento);
            Files.writeString(
                caminhoArquivo,
                linha + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            System.out.println("[Lamport " + timestampLamport + "] " + tipo + " " + linha);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar evento no log da " + nomeAgencia, e);
        }
        return evento;
    }

    /**
     * Monta o mapa de detalhes preservando a ordem em que os campos foram escritos.
     * Map.of() nao serve aqui: ele nao garante ordem, e o log sairia com os campos
     * embaralhados a cada linha, atrapalhando a leitura da linha do tempo da Parte E.
     */
    public static Map<String, Object> detalhes(Object... chaveValor) {
        if (chaveValor.length % 2 != 0) {
            throw new IllegalArgumentException("detalhes() espera pares chave/valor");
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < chaveValor.length; i += 2) {
            mapa.put(String.valueOf(chaveValor[i]), chaveValor[i + 1]);
        }
        return mapa;
    }

    public String getNomeAgencia() {
        return nomeAgencia;
    }

    public Path getCaminhoArquivo() {
        return caminhoArquivo;
    }
}
