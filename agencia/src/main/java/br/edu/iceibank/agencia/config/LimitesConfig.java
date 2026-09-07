package br.edu.iceibank.agencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * FUNCIONALIDADE ADICIONAL (secao 2.1): limite por operacao.
 *
 * Regra de negocio nova, que nao existe em nenhuma parte do roteiro: nenhum saque ou
 * transferencia pode passar de um teto por operacao, e o teto e configuravel por agencia.
 *
 * Deposito NAO tem teto, de proposito: limite de operacao existe para conter perda em caso de
 * credencial comprometida, e depositar dinheiro na propria conta nao causa perda. Bancos reais
 * seguem a mesma logica - o limite e sempre sobre a saida de dinheiro.
 */
@Component
public class LimitesConfig {

    private final BigDecimal limiteSaque;
    private final BigDecimal limiteTransferencia;

    public LimitesConfig(
            @Value("${iceibank.limites.saque}") BigDecimal limiteSaque,
            @Value("${iceibank.limites.transferencia}") BigDecimal limiteTransferencia) {
        this.limiteSaque = limiteSaque;
        this.limiteTransferencia = limiteTransferencia;
    }

    public BigDecimal getLimiteSaque() {
        return limiteSaque;
    }

    public BigDecimal getLimiteTransferencia() {
        return limiteTransferencia;
    }

    public boolean saqueAcimaDoLimite(BigDecimal valor) {
        return valor.compareTo(limiteSaque) > 0;
    }

    public boolean transferenciaAcimaDoLimite(BigDecimal valor) {
        return valor.compareTo(limiteTransferencia) > 0;
    }
}
