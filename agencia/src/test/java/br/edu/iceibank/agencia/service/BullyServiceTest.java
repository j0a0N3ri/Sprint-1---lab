package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BullyServiceTest {
    @TempDir Path logs;

    @Test
    void convergeEReelegeAoPerderAAgenciaDoMaiorId() throws Exception {
        ObjectMapper json = JsonMapper.builder().build();
        var ativas = new ConcurrentHashMap<Integer, BullyService>();
        Function<HttpRequest, String> rede = req -> {
            int destino = req.uri().getPort() - AgenciaConfig.PORTA_BASE;
            BullyService no = ativas.get(destino);
            if (no == null) return null;

            String retorno = "";
            switch (req.uri().getPath()) {
                case "/bully/lider" -> retorno = json.writeValueAsString(no.estado());
                case "/bully/eleicao" -> no.receber(json.readValue(corpo(req), BullyService.Eleicao.class));
                case "/bully/coordenador" -> no.receber(json.readValue(corpo(req), BullyService.Coordenador.class));
                default -> throw new IllegalArgumentException(req.uri().toString());
            }
            return retorno;
        };

        List<BullyService> nos = new ArrayList<>();
        try {
            for (int id = 0; id < 3; id++) {
                nos.add(new BullyService(id, new RelogioVetorial(id, 3),
                    new RegistroEventos("agencia-" + id, logs), json, rede));
            }

            // A subida escalonada deixa duas trocas provisórias observáveis antes de estabilizar.
            for (int id = 0; id < 3; id++) {
                int esperado = id;
                ativas.put(id, nos.get(id));
                nos.get(id).iniciarHeartbeat(Duration.ofMillis(80));
                aguardar(() -> ativas.values().stream()
                    .allMatch(no -> Objects.equals(no.estado().liderAtual(), esperado)));
            }
            System.out.println("Bully com transporte de teste: 0 → 1 → 2 durante a subida.");

            ativas.remove(2);
            nos.get(2).fechar();
            aguardar(() -> ativas.values().stream().allMatch(no -> Objects.equals(no.estado().liderAtual(), 1)));
            System.out.println("Bully com transporte de teste: failover automático de 2 para 1.");
        } finally {
            nos.forEach(BullyService::fechar);
        }
    }

    private static void aguardar(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(6).toNanos();
        while (!condicao.getAsBoolean() && System.nanoTime() < limite) {
            Thread.sleep(15);
        }
        assertTrue(condicao.getAsBoolean(), "As agências ativas deveriam convergir.");
    }

    private static String corpo(HttpRequest req) {
        var bytes = new ByteArrayOutputStream();
        req.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription fluxo) { fluxo.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer bloco) {
                byte[] parte = new byte[bloco.remaining()];
                bloco.get(parte);
                bytes.writeBytes(parte);
            }
            @Override public void onError(Throwable erro) { throw new IllegalStateException(erro); }
            @Override public void onComplete() {}
        });
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
