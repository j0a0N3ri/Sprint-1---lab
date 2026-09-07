package br.edu.iceibank.agencia.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Linha do tempo unificada (Parte E).
 *
 * Le os .jsonl de todas as agencias e monta UMA linha do tempo ordenada pelo relogio de
 * Lamport - nao pela hora de parede. E aqui que o algoritmo fica observavel: eventos de
 * processos diferentes, que nunca compartilharam memoria, aparecem numa ordem que respeita
 * a causalidade entre eles.
 *
 * Alem de imprimir a linha do tempo, o programa aponta duas coisas que interessam para as
 * perguntas da secao 10.3:
 *   - EMPATES: mesmo timestamp em agencias diferentes = eventos concorrentes, sem relacao
 *     causal. Lamport nao sabe orden√°-los, e nao precisa.
 *   - DIVERGENCIAS: pares em que a ordem por Lamport contraria a ordem por hora de parede.
 *
 * Execucao:  mvn -q exec:java
 */
public class MesclarLogs {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public static void main(String[] args) throws IOException {
        Path pastaDados = Paths.get(args.length > 0 ? args[0] : "data");

        if (!Files.isDirectory(pastaDados)) {
            System.err.println("Pasta de dados nao encontrada: " + pastaDados.toAbsolutePath());
            System.err.println("Rode as agencias e gere alguns eventos antes de mesclar os logs.");
            System.exit(1);
        }

        List<Map<String, Object>> eventos = lerEventos(pastaDados);
        if (eventos.isEmpty()) {
            System.err.println("Nenhum evento encontrado em " + pastaDados.toAbsolutePath());
            System.exit(1);
        }

        // A ordenacao e por relogio de Lamport. O desempate por agencia NAO tem significado
        // causal: serve so para a saida ser estavel entre execucoes.
        eventos.sort(Comparator
            .comparingInt((Map<String, Object> e) -> (int) e.get("timestampLamport"))
            .thenComparing(e -> (String) e.get("agencia")));

        imprimirLinhaDoTempo(eventos);
        imprimirEmpates(eventos);
        imprimirDivergenciasDeOrdem(eventos);
    }

    private static List<Map<String, Object>> lerEventos(Path pastaDados) throws IOException {
        List<Map<String, Object>> eventos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(pastaDados)) {
            for (Path arquivo : arquivos.filter(a -> a.toString().endsWith(".jsonl")).sorted().toList()) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (linha.isBlank()) {
                        continue;
                    }
                    JsonNode no = JSON.readTree(linha);
                    Map<String, Object> evento = new LinkedHashMap<>();
                    evento.put("agencia", no.get("agencia").asString());
                    evento.put("tipo", no.get("tipo").asString());
                    evento.put("timestampLamport", no.get("timestampLamport").asInt());
                    evento.put("horaParede", no.get("horaParede").asString());
                    evento.put("detalhes", no.get("detalhes").toString());
                    eventos.add(evento);
                }
            }
        }
        return eventos;
    }

    private static void imprimirLinhaDoTempo(List<Map<String, Object>> eventos) {
        System.out.println("=== Linha do tempo unificada (ordenada por relogio de Lamport) ===");
        System.out.println();
        for (Map<String, Object> e : eventos) {
            System.out.printf("[Lamport %3d] (%s) %-9s %-28s %s%n",
                e.get("timestampLamport"),
                e.get("horaParede"),
                e.get("agencia"),
                e.get("tipo"),
                e.get("detalhes"));
        }
        System.out.println();
        System.out.println("Total de eventos: " + eventos.size());
    }

    /** Mesmo timestamp em agencias diferentes: eventos concorrentes, sem relacao causal. */
    private static void imprimirEmpates(List<Map<String, Object>> eventos) {
        System.out.println();
        System.out.println("=== Empates de timestamp entre agencias diferentes (eventos concorrentes) ===");

        Map<Integer, List<Map<String, Object>>> porTimestamp = new LinkedHashMap<>();
        for (Map<String, Object> e : eventos) {
            porTimestamp.computeIfAbsent((Integer) e.get("timestampLamport"), k -> new ArrayList<>()).add(e);
        }

        int empates = 0;
        for (Map.Entry<Integer, List<Map<String, Object>>> entrada : porTimestamp.entrySet()) {
            long agenciasDistintas = entrada.getValue().stream().map(e -> e.get("agencia")).distinct().count();
            if (agenciasDistintas > 1) {
                empates++;
                System.out.println();
                System.out.println("  Lamport " + entrada.getKey() + ":");
                for (Map<String, Object> e : entrada.getValue()) {
                    System.out.printf("    %-9s %-28s hora de parede: %s%n",
                        e.get("agencia"), e.get("tipo"), e.get("horaParede"));
                }
            }
        }

        if (empates == 0) {
            System.out.println("  Nenhum empate entre agencias distintas nesta execucao.");
            System.out.println("  Gere mais eventos concorrentes (operacoes simultaneas em agencias diferentes).");
        } else {
            System.out.println();
            System.out.println("  " + empates + " timestamp(s) empatado(s) entre agencias diferentes.");
            System.out.println("  Empate = o relogio de Lamport NAO consegue ordenar esses eventos, e esta certo:");
            System.out.println("  eles sao concorrentes, nenhum influenciou o outro.");
        }
    }

    /** Pares em que a ordem por Lamport contraria a ordem por hora de parede. */
    private static void imprimirDivergenciasDeOrdem(List<Map<String, Object>> eventos) {
        System.out.println();
        System.out.println("=== Lamport x hora de parede: onde as duas ordens discordam ===");

        List<Map<String, Object>> porHoraParede = new ArrayList<>(eventos);
        porHoraParede.sort(Comparator.comparing(e -> (String) e.get("horaParede")));

        int divergencias = 0;
        for (int i = 0; i < eventos.size(); i++) {
            Map<String, Object> porLamport = eventos.get(i);
            Map<String, Object> porRelogio = porHoraParede.get(i);
            if (porLamport != porRelogio) {
                divergencias++;
                if (divergencias <= 5) {
                    System.out.printf("  posicao %d: por Lamport -> %s %s (ts %s) | por hora de parede -> %s %s (ts %s)%n",
                        i + 1,
                        porLamport.get("agencia"), porLamport.get("tipo"), porLamport.get("timestampLamport"),
                        porRelogio.get("agencia"), porRelogio.get("tipo"), porRelogio.get("timestampLamport"));
                }
            }
        }

        if (divergencias == 0) {
            System.out.println("  As duas ordens coincidiram nesta execucao.");
            System.out.println("  Isso e acidente de teste local (uma so maquina, mesmo relogio fisico), nao garantia:");
            System.out.println("  em maquinas diferentes os relogios fisicos derivam e a coincidencia some.");
        } else {
            System.out.println();
            System.out.println("  " + divergencias + " posicao(oes) em que as duas ordens discordam.");
            System.out.println("  Ordenar por hora de parede daria uma historia diferente da causal.");
        }
    }
}
