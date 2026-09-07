package br.edu.iceibank.agencia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Le o cabecalho Authorization: Bearer <token> de cada requisicao, valida o token e coloca a
 * identidade no contexto de seguranca do Spring.
 *
 * O filtro nao rejeita ninguem por conta propria: se nao ha token valido, ele simplesmente
 * deixa o contexto vazio e segue. Quem decide o que exige autenticacao e o SecurityConfig -
 * assim a regra de acesso fica num lugar so, e nao espalhada entre filtro e configuracao.
 */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwt;

    public JwtFiltro(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String cabecalho = req.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith(PREFIXO)) {
            UsuarioAutenticado autenticado = jwt.validar(cabecalho.substring(PREFIXO.length()).trim());
            if (autenticado != null) {
                var autoridade = new SimpleGrantedAuthority("ROLE_" + autenticado.tipo().name());
                var auth = new UsernamePasswordAuthenticationToken(
                    autenticado, null, List.of(autoridade));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(req, res);
    }
}
