package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class BullyService {
    public record Estado(int idAgencia, Integer liderAtual) {}
    public record Eleicao(Integer origemId, int[] vetorEnvio) {}
    public record Coordenador(Integer liderId, int[] vetorEnvio) {}

    private final int id;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final ObjectMapper json;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private volatile Integer lider;
    private long esperarCoordenadorAte;

    public BullyService(@Value("${agencia.id}") int id, RelogioVetorial relogio,
                       RegistroEventos registro, ObjectMapper json) {
        this.id = id;
        this.relogio = relogio;
        this.registro = registro;
        this.json = json;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        worker.scheduleWithFixedDelay(() -> executar(this::verificar), 0, 3, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void fechar() {
        worker.shutdownNow();
    }

    public Estado estado() {
        return new Estado(id, lider);
    }

    // O HTTP devolve o OK imediatamente. Um único worker serializa eleições e anúncios.
    public void receber(Eleicao mensagem) {
        enfileirar(() -> {
            registrar("ELEICAO_RECEBIDA", relogio.aoReceber(mensagem.vetorEnvio()),
                Map.of("de", mensagem.origemId(), "minhaId", id));
            if (Objects.equals(lider, id)) {
                enviar(mensagem.origemId(), "/bully/coordenador", new Coordenador(id, relogio.aoEnviar()));
            } else if (System.nanoTime() >= esperarCoordenadorAte) {
                eleger();
            }
        });
    }

    public void receber(Coordenador mensagem) {
        enfileirar(() -> {
            int[] vetor = relogio.aoReceber(mensagem.vetorEnvio());
            if (mensagem.liderId() >= id && (lider == null || mensagem.liderId() >= lider)) {
                lider = mensagem.liderId();
                esperarCoordenadorAte = 0;
                registrar("LIDER_ANUNCIADO", vetor, Map.of("liderId", lider, "agencia", id));
            }
        });
    }

    private void enfileirar(Runnable tarefa) {
        try {
            worker.execute(() -> executar(tarefa));
        } catch (RejectedExecutionException ignored) {
            // O processo está encerrando.
        }
    }

    private void executar(Runnable tarefa) {
        try {
            tarefa.run();
        } catch (RuntimeException erro) {
            LoggerFactory.getLogger(BullyService.class).warn("Erro ao processar eleição", erro);
        }
    }

    private void verificar() {
        if (lider == null) {
            if (System.nanoTime() >= esperarCoordenadorAte) eleger();
            return;
        }
        if (lider == id) {
            // Reconcilia anúncios perdidos quando duas agências ficaram isoladas.
            for (var agencia : AgenciaConfig.AGENCIAS) {
                if (agencia.id() > id) {
                    Estado maior = consultar(agencia.id());
                    if (maior != null && Objects.equals(maior.liderAtual(), maior.idAgencia())) {
                        eleger();
                        return;
                    }
                }
            }
            return;
        }
        Estado remoto = consultar(lider);
        if (remoto == null || !Objects.equals(remoto.liderAtual(), lider)) {
            registrar("LIDER_NAO_RESPONDEU", relogio.eventoLocal(), Map.of("liderAtual", lider, "agencia", id));
            lider = null;
            eleger();
        }
    }

    private void eleger() {
        registrar("ELEICAO_INICIADA", relogio.eventoLocal(), Map.of("idAgencia", id));
        boolean maiorRespondeu = false;
        for (var agencia : AgenciaConfig.AGENCIAS) {
            if (agencia.id() > id) {
                maiorRespondeu |= enviar(agencia.id(), "/bully/eleicao", new Eleicao(id, relogio.aoEnviar()));
            }
        }
        if (maiorRespondeu) {
            lider = null;
            esperarCoordenadorAte = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            return;
        }
        lider = id;
        registrar("TORNOU_LIDER", relogio.eventoLocal(), Map.of("idAgencia", id));
        for (var agencia : AgenciaConfig.AGENCIAS) {
            if (agencia.id() != id) {
                enviar(agencia.id(), "/bully/coordenador", new Coordenador(id, relogio.aoEnviar()));
            }
        }
    }

    private Estado consultar(int destino) {
        String corpo = chamar(requisicao(destino, "/bully/lider").GET().build());
        return corpo == null ? null : json.readValue(corpo, Estado.class);
    }

    private boolean enviar(int destino, String rota, Object corpo) {
        return chamar(requisicao(destino, rota).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo))).build()) != null;
    }

    private HttpRequest.Builder requisicao(int destino, String rota) {
        return HttpRequest.newBuilder(URI.create(AgenciaConfig.AGENCIAS.get(destino).url() + rota))
            .timeout(Duration.ofSeconds(1));
    }

    private String chamar(HttpRequest req) {
        try {
            var resposta = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resposta.statusCode() == 200 ? resposta.body() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void registrar(String tipo, int[] vetor, Map<String, Object> detalhes) {
        registro.registrar(tipo, vetor, detalhes);
    }
}
