package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.controller.dto.CreditoRemotoRequest;
import br.edu.iceibank.agencia.security.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Chamada REST de uma agencia para outra. E o unico ponto do sistema onde uma agencia fala
 * com outra - a "mensagem" do modelo de Lamport tem endereco fisico: este metodo.
 *
 * Os timeouts sao curtos de proposito. Sem eles, uma agencia de destino que aceita a conexao
 * mas nao responde deixaria a agencia de origem pendurada indefinidamente, segurando a
 * requisicao do cliente. Com timeout, a falha vira um erro rapido e observavel - que e
 * exatamente o cenario da falha conhecida da Parte D.
 */
@Service
public class ClienteAgencias {

    private final RestClient http;
    private final JwtService jwt;

    public ClienteAgencias(JwtService jwt) {
        this.jwt = jwt;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    /**
     * A chamada entre agencias tambem vai autenticada, com um token de SERVICO proprio -
     * nao com o token da pessoa que iniciou a transferencia. Ver a justificativa em
     * RESPOSTAS.md (Parte F).
     */
    public void creditarRemoto(String urlAgenciaDestino, int idConta, CreditoRemotoRequest corpo, int idAgenciaOrigem) {
        http.post()
            .uri(urlAgenciaDestino + "/contas/" + idConta + "/creditar-remoto")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.gerarTokenDeServico(idAgenciaOrigem))
            .body(corpo)
            .retrieve()
            .toBodilessEntity();
    }
}
