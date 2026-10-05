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
import java.util.List;
import java.util.stream.Stream;

/** Mostra os eventos e identifica relacoes causais por comparacao de vetores. */
public class MesclarLogs {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public enum Relacao { IGUAIS, ANTES, DEPOIS, CONCORRENTES }

    private record Evento(String agencia, String tipo, int[] vetor,
                          String horaParede, String detalhes) {}

    public static void main(String[] args) throws IOException {
        Path pastaDados = Paths.get(args.length > 0 ? args[0] : "data");
        if (!Files.isDirectory(pastaDados)) {
            System.err.println("Pasta de dados nao encontrada: " + pastaDados.toAbsolutePath());
            System.exit(1);
        }

        List<Evento> eventos = lerEventos(pastaDados);
        if (eventos.isEmpty()) {
            System.err.println("Nenhum evento vetorial encontrado. Gere eventos da Sprint 2 primeiro.");
            System.exit(1);
        }

        eventos.sort(Comparator.comparing(Evento::horaParede));
        imprimirLinhaDoTempo(eventos);
        imprimirConcorrentes(eventos);
    }

    static List<Evento> lerEventos(Path pastaDados) throws IOException {
        List<Evento> eventos = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(pastaDados)) {
            for (Path arquivo : arquivos.filter(a -> a.toString().endsWith(".jsonl")).sorted().toList()) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (linha.isBlank()) continue;
                    JsonNode no = JSON.readTree(linha);
                    JsonNode vetorNo = no.get("timestampVetorial");
                    // Logs do Sprint 1 usam timestampLamport e nao podem ser comparados
                    // como vetores. Eles ficam preservados no disco, mas fora desta analise.
                    if (vetorNo == null || !vetorNo.isArray()) continue;
                    int[] vetor = new int[vetorNo.size()];
                    for (int i = 0; i < vetor.length; i++) vetor[i] = vetorNo.get(i).asInt();
                    eventos.add(new Evento(
                        no.get("agencia").asString(),
                        no.get("tipo").asString(),
                        vetor,
                        no.get("horaParede").asString(),
                        no.get("detalhes").toString()
                    ));
                }
            }
        }
        return eventos;
    }

    public static Relacao compararVetores(int[] primeiro, int[] segundo) {
        if (primeiro == null || segundo == null || primeiro.length != segundo.length) {
            throw new IllegalArgumentException("Vetores precisam ter o mesmo tamanho.");
        }
        boolean primeiroMenorOuIgual = true;
        boolean segundoMenorOuIgual = true;
        for (int i = 0; i < primeiro.length; i++) {
            if (primeiro[i] > segundo[i]) primeiroMenorOuIgual = false;
            if (segundo[i] > primeiro[i]) segundoMenorOuIgual = false;
        }
        if (primeiroMenorOuIgual && segundoMenorOuIgual) return Relacao.IGUAIS;
        if (primeiroMenorOuIgual) return Relacao.ANTES;
        if (segundoMenorOuIgual) return Relacao.DEPOIS;
        return Relacao.CONCORRENTES;
    }

    private static void imprimirLinhaDoTempo(List<Evento> eventos) {
        System.out.println("=== Linha do tempo causal (exibida por hora de parede) ===");
        for (Evento evento : eventos) {
            System.out.printf("[%s] vetor=%s %-30s %s%n",
                evento.agencia(), java.util.Arrays.toString(evento.vetor()),
                evento.tipo(), evento.detalhes());
        }
        System.out.println("Total de eventos vetoriais: " + eventos.size());
    }

    private static void imprimirConcorrentes(List<Evento> eventos) {
        System.out.println();
        System.out.println("=== Pares CONCORRENTES entre agencias diferentes ===");
        int quantidade = 0;
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = i + 1; j < eventos.size(); j++) {
                Evento primeiro = eventos.get(i);
                Evento segundo = eventos.get(j);
                if (primeiro.agencia().equals(segundo.agencia())) continue;
                if (compararVetores(primeiro.vetor(), segundo.vetor()) == Relacao.CONCORRENTES) {
                    quantidade++;
                    System.out.printf("[%s] %s %s  x  [%s] %s %s%n",
                        primeiro.agencia(), primeiro.tipo(), java.util.Arrays.toString(primeiro.vetor()),
                        segundo.agencia(), segundo.tipo(), java.util.Arrays.toString(segundo.vetor()));
                }
            }
        }
        if (quantidade == 0) {
            System.out.println("Nenhum par concorrente encontrado nesta execucao.");
        } else {
            System.out.println("Total de pares concorrentes: " + quantidade);
        }
    }
}
