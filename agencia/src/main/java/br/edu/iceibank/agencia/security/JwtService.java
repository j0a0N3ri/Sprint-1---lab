package br.edu.iceibank.agencia.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emissao e validacao dos tokens JWT.
 *
 * A assinatura e HMAC-SHA256 (algoritmo simetrico): a MESMA chave assina e verifica. Foi a
 * escolha certa para este sistema porque as tres agencias sao do mesmo dono e ja compartilham
 * configuracao - qualquer uma precisa validar o token de servico emitido por outra. Se as
 * agencias fossem de organizacoes diferentes, o correto seria um algoritmo assimetrico (RS256),
 * onde cada emissor guarda a chave privada e as outras so recebem a publica.
 *
 * Nao ha consulta a banco para validar um token: a assinatura e verificada matematicamente,
 * com a chave que o processo ja tem em memoria.
 */
@Service
public class JwtService {

    private static final String CLAIM_TIPO = "tipo";
    private static final String EMISSOR = "iceibank";

    private final SecretKey chave;
    private final long expiracaoSegundos;

    public JwtService(@Value("${iceibank.jwt.segredo}") String segredo,
                      @Value("${iceibank.jwt.expiracao-segundos}") long expiracaoSegundos) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HMAC-SHA256 exige chave de no minimo 256 bits. Falhar na subida e melhor do que
            // descobrir isso na primeira tentativa de login.
            throw new IllegalStateException("iceibank.jwt.segredo precisa ter ao menos 32 caracteres.");
        }
        this.chave = new SecretKeySpec(bytes, "HmacSHA256");
        this.expiracaoSegundos = expiracaoSegundos;
    }

    /** Token de uma pessoa. Expira: um token eterno vazado vale para sempre. */
    public String gerarTokenDeUsuario(String login) {
        return gerar(login, UsuarioAutenticado.Tipo.USUARIO, expiracaoSegundos);
    }

    /**
     * Token de servico, usado por uma agencia para falar com outra no credito remoto.
     * Vida curta (60s): ele nasce, atravessa uma unica chamada e morre. Nao ha usuario
     * envolvido nessa ponta, entao nao faz sentido carregar a identidade de ninguem.
     */
    public String gerarTokenDeServico(int idAgencia) {
        return gerar("agencia-" + idAgencia, UsuarioAutenticado.Tipo.SERVICO, 60);
    }

    private String gerar(String assunto, UsuarioAutenticado.Tipo tipo, long segundos) {
        Instant agora = Instant.now();
        return Jwts.builder()
            .issuer(EMISSOR)
            .subject(assunto)
            .claim(CLAIM_TIPO, tipo.name())
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plusSeconds(segundos)))
            .signWith(chave)
            .compact();
    }

    /**
     * Valida assinatura, emissor e expiracao. Devolve null se o token for invalido por
     * qualquer motivo - quem chama traduz isso em 401.
     */
    public UsuarioAutenticado validar(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(EMISSOR)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String tipo = claims.get(CLAIM_TIPO, String.class);
            if (tipo == null) {
                return null;
            }
            return new UsuarioAutenticado(claims.getSubject(), UsuarioAutenticado.Tipo.valueOf(tipo));
        } catch (JwtException | IllegalArgumentException e) {
            // Assinatura invalida, token expirado, emissor errado, formato quebrado:
            // do ponto de vista de quem chama e tudo a mesma coisa - o token nao vale.
            return null;
        }
    }

    public long getExpiracaoSegundos() {
        return expiracaoSegundos;
    }
}
