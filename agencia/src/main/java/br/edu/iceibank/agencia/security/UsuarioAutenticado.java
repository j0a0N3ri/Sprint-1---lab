package br.edu.iceibank.agencia.security;

/**
 * Quem esta por tras da requisicao, ja validado pelo filtro JWT.
 *
 * O sistema tem dois tipos de identidade, e a diferenca importa:
 *   USUARIO - uma pessoa, que so pode mexer nas contas dela;
 *   SERVICO - outra agencia falando com esta, na chamada de credito remoto.
 */
public record UsuarioAutenticado(String nome, Tipo tipo) {

    public enum Tipo { USUARIO, SERVICO }

    public boolean eServico() {
        return tipo == Tipo.SERVICO;
    }
}
