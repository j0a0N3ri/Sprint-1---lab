package br.edu.iceibank.agencia.security;

import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ponte entre o contexto de seguranca do Spring e as regras da aplicacao.
 *
 * E aqui que mora a diferenca entre AUTENTICACAO e AUTORIZACAO: o filtro ja disse QUEM e
 * (autenticacao); estes metodos respondem se essa pessoa PODE fazer aquilo (autorizacao).
 */
@Component
public class ContextoDeSeguranca {

    private final UsuarioRepositorio usuarios;

    public ContextoDeSeguranca(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    public UsuarioAutenticado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado autenticado)) {
            throw new ErroDeNegocio(HttpStatus.UNAUTHORIZED, "Requisicao sem autenticacao valida.");
        }
        return autenticado;
    }

    /**
     * Exige que quem esta chamando seja dono da conta.
     *
     * Chamadas de SERVICO (agencia falando com agencia, no credito remoto) passam direto:
     * do outro lado nao ha um usuario, ha um processo. Sem essa excecao, uma transferencia
     * entre agencias so funcionaria se a mesma pessoa fosse dona das duas contas.
     */
    public void exigirDonoDaConta(int idConta) {
        UsuarioAutenticado atual = atual();
        if (atual.eServico()) {
            return;
        }
        boolean dono = usuarios.buscar(atual.nome())
            .map(u -> u.eDonoDa(idConta))
            .orElse(false);
        if (!dono) {
            throw new ErroDeNegocio(HttpStatus.FORBIDDEN,
                "A conta " + idConta + " nao pertence ao usuario autenticado.");
        }
    }

    /** Versao que responde sim/nao em vez de lancar - usada para filtrar listagens. */
    public boolean podeAcessarConta(int idConta) {
        try {
            exigirDonoDaConta(idConta);
            return true;
        } catch (ErroDeNegocio e) {
            return false;
        }
    }

    /** Registra que quem criou a conta e o dono dela. */
    public void vincularContaAoUsuarioAtual(int idConta) {
        UsuarioAutenticado atual = atual();
        if (atual.eServico()) {
            return;
        }
        usuarios.buscar(atual.nome()).ifPresent(u -> u.vincularConta(idConta));
    }
}
