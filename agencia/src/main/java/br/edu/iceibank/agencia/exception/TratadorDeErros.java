package br.edu.iceibank.agencia.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Converte qualquer ErroDeNegocio lancado nos services no mesmo formato de resposta
 * que o roteiro usa: { "erro": "..." }, com o status HTTP correspondente.
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(ErroDeNegocio.class)
    public ResponseEntity<Map<String, String>> tratar(ErroDeNegocio e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("erro", e.getMessage()));
    }
}
