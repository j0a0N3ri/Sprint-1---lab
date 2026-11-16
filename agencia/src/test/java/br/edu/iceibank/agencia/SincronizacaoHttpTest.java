package br.edu.iceibank.agencia;

import jakarta.servlet.Filter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false", "iceibank.bully.enabled=false"
})
class SincronizacaoHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    @Test
    void rejeitaVetorAntigoEAplicaUmaVezComVetorAtual() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        String login = mvc.perform(post("/auth/login").contentType("application/json")
                .content("""
                    {"login":"ana","senha":"senha123"}
                    """))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(login).get("token").asText();

        mvc.perform(post("/contas").header("Authorization", token).contentType("application/json")
                .content("""
                    {"id":90,"nomeAluno":"Ana","saldoInicial":100}
                    """))
            .andExpect(status().isCreated());
        var leitura = mvc.perform(get("/contas/90").header("Authorization", token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mvc.perform(post("/contas/90/depositar").header("Authorization", token)
                .contentType("application/json").content("{\"valor\":25}"))
            .andExpect(status().isOk());
        UUID operacaoId = UUID.randomUUID();
        var vetorAntigo = json.readTree(leitura).get("vetorAtual");
        var caminho = "/contas/90/sincronizar";
        var conflito = mvc.perform(post(caminho).header("Authorization", token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("tipo", "DEPOSITO", "valor", 10,
                    "vetorConhecido", vetorAntigo, "operacaoId", operacaoId))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.contaAtual.saldo").value(125))
            .andReturn().getResponse().getContentAsString();

        var vetorAtual = json.readTree(conflito).get("vetorAtual");
        var operacao = json.writeValueAsString(Map.of("tipo", "DEPOSITO", "valor", 10,
            "vetorConhecido", vetorAtual, "operacaoId", operacaoId));
        mvc.perform(post(caminho).header("Authorization", token).contentType("application/json").content(operacao))
            .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(135));
        mvc.perform(post(caminho).header("Authorization", token).contentType("application/json").content(operacao))
            .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(135));
        mvc.perform(post(caminho).contentType("application/json").content(operacao))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/bully/lider")).andExpect(status().isOk())
            .andExpect(jsonPath("$.idAgencia").value(0));
        System.out.println("HTTP 409 verificado pelo MockMvc: " + conflito);
    }
}
