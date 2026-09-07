package br.edu.iceibank.agencia.model;

import java.math.BigDecimal;

/**
 * Uma conta do ICEIBank. Pertence a exatamente uma agencia (particao, nao replicacao):
 * quem manda e o resultado de AgenciaConfig.agenciaResponsavel(id).
 *
 * O saldo e BigDecimal, nao double: dinheiro em ponto flutuante binario acumula erro de
 * arredondamento (0.1 + 0.2 nao da 0.3), e o dominio inteiro deste projeto existe para
 * deixar visivel se "o saldo bate ou nao bate".
 */
public class Conta {

    private final int id;
    private final String nomeAluno;
    private BigDecimal saldo;

    public Conta(int id, String nomeAluno, BigDecimal saldoInicial) {
        this.id = id;
        this.nomeAluno = nomeAluno;
        this.saldo = saldoInicial == null ? BigDecimal.ZERO : saldoInicial;
    }

    public int getId() {
        return id;
    }

    public String getNomeAluno() {
        return nomeAluno;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void creditar(BigDecimal valor) {
        this.saldo = this.saldo.add(valor);
    }

    public void debitar(BigDecimal valor) {
        this.saldo = this.saldo.subtract(valor);
    }

    public boolean temSaldoPara(BigDecimal valor) {
        return this.saldo.compareTo(valor) >= 0;
    }
}
