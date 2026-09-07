package br.edu.iceibank.agencia.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negocio que ja sabe com qual status HTTP deve sair.
 * Concentrar isso aqui deixa os controllers finos: eles nao ficam montando ResponseEntity
 * de erro em cada ramo, quem faz isso e o TratadorDeErros.
 */
public class ErroDeNegocio extends RuntimeException {

    private final HttpStatus status;

    public ErroDeNegocio(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ErroDeNegocio naoEncontrado(String mensagem) {
        return new ErroDeNegocio(HttpStatus.NOT_FOUND, mensagem);
    }

    public static ErroDeNegocio requisicaoInvalida(String mensagem) {
        return new ErroDeNegocio(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static ErroDeNegocio conflito(String mensagem) {
        return new ErroDeNegocio(HttpStatus.CONFLICT, mensagem);
    }
}
