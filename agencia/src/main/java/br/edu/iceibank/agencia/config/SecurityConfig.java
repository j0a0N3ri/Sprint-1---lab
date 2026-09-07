package br.edu.iceibank.agencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracao TEMPORARIA de seguranca.
 *
 * O spring-boot-starter-security esta no classpath e, por padrao, tranca todos os endpoints
 * atras de um formulario de login com senha gerada no console - o que impediria testar a API
 * com curl nas Partes C, D e E. Aqui liberamos tudo de proposito, seguindo a ordem do roteiro:
 * primeiro a API funciona e e testada, depois a Parte F a protege de verdade com JWT.
 *
 * O CSRF fica desligado porque esta e uma API REST sem sessao e sem cookie: nao existe
 * navegador enviando credencial automaticamente, que e o ataque que o token CSRF previne.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(req -> req.anyRequest().permitAll());
        return http.build();
    }
}
