package br.edu.iceibank.agencia.controller;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.service.BullyService;
import java.util.Arrays;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bully")
public class BullyController {
    private final BullyService bully;

    public BullyController(BullyService bully) {
        this.bully = bully;
    }

    @GetMapping("/lider")
    public BullyService.Estado lider() {
        return bully.estado();
    }

    @PostMapping("/eleicao")
    public void eleicao(@RequestBody BullyService.Eleicao mensagem) {
        validar(mensagem.origemId(), mensagem.vetorEnvio());
        if (mensagem.origemId() >= bully.estado().idAgencia()) {
            throw ErroDeNegocio.requisicaoInvalida("A eleição vai de um ID menor para um maior.");
        }
        bully.receber(mensagem);
    }

    @PostMapping("/coordenador")
    public void coordenador(@RequestBody BullyService.Coordenador mensagem) {
        validar(mensagem.liderId(), mensagem.vetorEnvio());
        bully.receber(mensagem);
    }

    private void validar(Integer id, int[] vetor) {
        if (id == null || id < 0 || id >= AgenciaConfig.NUMERO_AGENCIAS || vetor == null
                || vetor.length != AgenciaConfig.NUMERO_AGENCIAS || Arrays.stream(vetor).anyMatch(n -> n < 0)) {
            throw ErroDeNegocio.requisicaoInvalida("Mensagem Bully inválida.");
        }
    }
}
