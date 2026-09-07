package br.edu.iceibank.agencia.controller;

import br.edu.iceibank.agencia.config.LimitesConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expoe os limites vigentes nesta agencia (funcionalidade adicional).
 *
 * Sem esta rota, a regra so apareceria como erro depois da tentativa - a pessoa descobriria
 * o limite errando. Com ela, o frontend mostra o teto antes da operacao.
 */
@RestController
public class LimitesController {

    private final LimitesConfig limites;

    public LimitesController(LimitesConfig limites) {
        this.limites = limites;
    }

    @GetMapping("/limites")
    public Map<String, Object> consultar() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("limitePorSaque", limites.getLimiteSaque());
        resposta.put("limitePorTransferencia", limites.getLimiteTransferencia());
        resposta.put("observacao", "Deposito nao tem limite: a regra vale sobre saida de dinheiro.");
        return resposta;
    }
}
