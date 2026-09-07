package br.edu.iceibank.agencia.controller;

import br.edu.iceibank.agencia.controller.dto.CreditoRemotoRequest;
import br.edu.iceibank.agencia.controller.dto.TransferenciaRequest;
import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.service.TransferenciaService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TransferenciasController {

    private final TransferenciaService transferencias;

    public TransferenciasController(TransferenciaService transferencias) {
        this.transferencias = transferencias;
    }

    /** Entrada do cliente: transferir da conta de origem (desta agencia) para qualquer conta. */
    @PostMapping("/transferencias")
    public Map<String, Object> transferir(@RequestBody TransferenciaRequest req) {
        if (req.idOrigem() == null || req.idDestino() == null) {
            throw ErroDeNegocio.requisicaoInvalida("idOrigem e idDestino sao obrigatorios.");
        }
        return transferencias.transferir(req.idOrigem(), req.idDestino(), req.valor());
    }

    /** Entrada agencia-a-agencia: nao e chamada pelo usuario final, e sim por outra agencia. */
    @PostMapping("/contas/{id}/creditar-remoto")
    public Map<String, Object> creditarRemoto(@PathVariable int id, @RequestBody CreditoRemotoRequest req) {
        return transferencias.creditarRemoto(id, req);
    }
}
