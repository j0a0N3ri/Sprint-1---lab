package br.edu.iceibank.agencia.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Um usuario do ICEIBank e as contas que ele pode operar.
 *
 * A senha e guardada com hash BCrypt, nunca em texto puro: se a memoria do processo (ou um
 * dump dela) vazar, o atacante nao ganha as senhas de graca.
 */
public class Usuario {

    private final String login;
    private final String senhaHash;
    private final Set<Integer> contas = new LinkedHashSet<>();

    public Usuario(String login, String senhaHash) {
        this.login = login;
        this.senhaHash = senhaHash;
    }

    public String getLogin() {
        return login;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Set<Integer> getContas() {
        return contas;
    }

    public void vincularConta(int idConta) {
        contas.add(idConta);
    }

    public boolean eDonoDa(int idConta) {
        return contas.contains(idConta);
    }
}
