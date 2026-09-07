package br.edu.iceibank.agencia.controller;

import br.edu.iceibank.agencia.controller.dto.LoginRequest;
import br.edu.iceibank.agencia.controller.dto.LoginResponse;
import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.security.JwtService;
import br.edu.iceibank.agencia.security.Usuario;
import br.edu.iceibank.agencia.security.UsuarioRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioRepositorio usuarios;
    private final JwtService jwt;

    public AuthController(UsuarioRepositorio usuarios, JwtService jwt) {
        this.usuarios = usuarios;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        Usuario usuario = usuarios.buscar(req.login() == null ? "" : req.login())
            .filter(u -> usuarios.senhaConfere(u, req.senha()))
            // Mensagem unica para login inexistente e senha errada, de proposito: distinguir
            // os dois casos entregaria de graca a lista de logins validos do sistema.
            .orElseThrow(() -> new ErroDeNegocio(HttpStatus.UNAUTHORIZED, "Credenciais invalidas."));

        return new LoginResponse(
            jwt.gerarTokenDeUsuario(usuario.getLogin()),
            usuario.getLogin(),
            jwt.getExpiracaoSegundos(),
            usuario.getContas()
        );
    }
}
