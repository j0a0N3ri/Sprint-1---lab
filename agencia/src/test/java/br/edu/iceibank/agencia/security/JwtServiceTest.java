package br.edu.iceibank.agencia.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Os tres cenarios que a Parte F exige, testados sem subir servidor.
 * O cenario "sem token" nao aparece aqui porque e responsabilidade do SecurityConfig,
 * nao do JwtService - ele e verificado na execucao real, com curl.
 */
class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres-ok";

    @Test
    void tokenValidoDevolveOUsuarioEOTipo() {
        JwtService jwt = new JwtService(SEGREDO, 900);
        UsuarioAutenticado autenticado = jwt.validar(jwt.gerarTokenDeUsuario("ana"));

        assertNotNull(autenticado);
        assertEquals("ana", autenticado.nome());
        assertEquals(UsuarioAutenticado.Tipo.USUARIO, autenticado.tipo());
    }

    @Test
    void tokenDeServicoVemMarcadoComoServico() {
        JwtService jwt = new JwtService(SEGREDO, 900);
        UsuarioAutenticado autenticado = jwt.validar(jwt.gerarTokenDeServico(2));

        assertNotNull(autenticado);
        assertEquals("agencia-2", autenticado.nome());
        assertEquals(UsuarioAutenticado.Tipo.SERVICO, autenticado.tipo());
    }

    @Test
    void tokenExpiradoEhRejeitado() {
        // Expiracao negativa: o token ja nasce vencido, sem precisar esperar em teste.
        JwtService emissorVencido = new JwtService(SEGREDO, -10);
        String token = emissorVencido.gerarTokenDeUsuario("ana");

        JwtService validador = new JwtService(SEGREDO, 900);
        assertNull(validador.validar(token));
    }

    @Test
    void tokenAssinadoComOutraChaveEhRejeitado() {
        String token = new JwtService("uma-chave-completamente-diferente-da-outra-32", 900)
            .gerarTokenDeUsuario("ana");

        assertNull(new JwtService(SEGREDO, 900).validar(token));
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        JwtService jwt = new JwtService(SEGREDO, 900);
        String token = jwt.gerarTokenDeUsuario("ana");

        // Troca um caractere do payload: a assinatura deixa de bater com o conteudo.
        String[] partes = token.split("\\.");
        String adulterado = partes[0] + "." + partes[1].substring(0, partes[1].length() - 2) + "XY." + partes[2];

        assertNull(jwt.validar(adulterado));
    }

    @Test
    void segredoCurtoDemaisImpedeASubidaDaAgencia() {
        assertThrows(IllegalStateException.class, () -> new JwtService("curto", 900));
    }
}
