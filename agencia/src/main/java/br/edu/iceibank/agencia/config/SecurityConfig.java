package br.edu.iceibank.agencia.config;

import br.edu.iceibank.agencia.security.JwtFiltro;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Quem pode chamar o que (Parte F).
 *
 * Tres niveis de acesso:
 *   /auth/login              - publico, senao ninguem consegue o primeiro token;
 *   /contas/{id}/creditar-remoto - so token de SERVICO (agencia falando com agencia);
 *   todo o resto             - so token de USUARIO (pessoa autenticada).
 *
 * A sessao e STATELESS: o servidor nao guarda nada entre requisicoes. Toda a identidade vem
 * do token, a cada chamada. E o que permite as 3 agencias aceitarem o mesmo token sem
 * compartilhar memoria nem consultar um banco de sessoes.
 */
@Configuration
public class SecurityConfig {

    private final JwtFiltro jwtFiltro;

    public SecurityConfig(JwtFiltro jwtFiltro) {
        this.jwtFiltro = jwtFiltro;
    }

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            // Sem CSRF: API REST sem cookie de sessao. O ataque que o token CSRF previne
            // depende de o navegador anexar credencial sozinho, e aqui o token vai
            // explicitamente no cabecalho Authorization, por codigo nosso.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(configuracaoCors()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(req -> req
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/contas/*/creditar-remoto").hasRole("SERVICO")
                .anyRequest().hasRole("USUARIO"))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> responder(res, 401,
                    "Token ausente, invalido ou expirado."))
                .accessDeniedHandler((req, res, ex) -> responder(res, 403,
                    "Token valido, mas sem permissao para esta operacao.")))
            .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Mantem o formato de erro { "erro": "..." } usado no resto da API. */
    private void responder(jakarta.servlet.http.HttpServletResponse res, int status, String mensagem)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"erro\":\"" + mensagem + "\"}");
    }

    /**
     * O frontend (Parte G) roda em outra origem que nao a da API, entao o navegador exige CORS.
     * Em producao a lista de origens seria restrita; aqui e aberta porque tudo roda em localhost.
     */
    @Bean
    public UrlBasedCorsConfigurationSource configuracaoCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", config);
        return fonte;
    }
}
