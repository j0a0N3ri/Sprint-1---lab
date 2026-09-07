package br.edu.iceibank.agencia.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Usuarios em memoria, no mesmo espirito das contas: este sprint nao tem banco de dados.
 *
 * A vinculacao conta-usuario acontece na criacao da conta: quem cria uma conta vira dono dela.
 * E isso que permite a agencia responder a pergunta "esta pessoa pode mexer nesta conta?" -
 * ou seja, fazer AUTORIZACAO, e nao so autenticacao.
 */
@Repository
public class UsuarioRepositorio {

    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public UsuarioRepositorio() {
        // Usuarios de demonstracao. Num sistema real viriam de um cadastro; aqui sao fixos
        // para que as tres agencias reconhecam as mesmas credenciais.
        cadastrar("ana", "senha123");
        cadastrar("bruno", "senha123");
        cadastrar("carla", "senha123");
        cadastrar("davi", "senha123");
    }

    private void cadastrar(String login, String senhaEmTextoPuro) {
        usuarios.put(login, new Usuario(login, encoder.encode(senhaEmTextoPuro)));
    }

    public Optional<Usuario> buscar(String login) {
        return Optional.ofNullable(usuarios.get(login));
    }

    /**
     * Confere a senha contra o hash guardado. O BCrypt faz a comparacao em tempo constante
     * em relacao ao conteudo, o que evita vazar informacao por diferenca de tempo de resposta.
     */
    public boolean senhaConfere(Usuario usuario, String senhaEmTextoPuro) {
        return senhaEmTextoPuro != null && encoder.matches(senhaEmTextoPuro, usuario.getSenhaHash());
    }
}
