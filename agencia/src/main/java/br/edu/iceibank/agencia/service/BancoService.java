package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.model.Conta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado e regras da agencia: as contas sob responsabilidade desta particao, o relogio de
 * Lamport e o log de eventos. Equivale ao app.locals do exemplo em Node.
 *
 * As contas vivem em memoria de proposito (nao ha banco de dados neste sprint): o foco e
 * REST/MVC e o relogio logico. Reiniciar a agencia zera as contas, e o esperado.
 *
 * Os metodos que mexem em saldo sao synchronized porque o Spring Boot atende requisicoes em
 * varias threads: sem isso, dois saques simultaneos na mesma conta poderiam ler o mesmo saldo
 * antes de qualquer um gravar, e a conta ficaria negativa mesmo com a checagem de saldo.
 */
@Service
public class BancoService {

    private final int idAgencia;
    private final RelogioLamport relogio;
    private final RegistroEventos registro;
    private final Map<Integer, Conta> contas = new ConcurrentHashMap<>();

    public BancoService(@Value("${agencia.id}") int idAgencia,
                        RelogioLamport relogio,
                        RegistroEventos registro) {
        this.idAgencia = idAgencia;
        this.relogio = relogio;
        this.registro = registro;
    }

    public int getIdAgencia() {
        return idAgencia;
    }

    public RelogioLamport getRelogio() {
        return relogio;
    }

    public RegistroEventos getRegistro() {
        return registro;
    }

    public int quantidadeDeContas() {
        return contas.size();
    }

    // --- consultas -------------------------------------------------------------------

    /** Busca a conta nesta agencia, ou explode com 404 se ela nao for daqui. */
    public Conta buscarConta(int id) {
        Conta conta = contas.get(id);
        if (conta == null) {
            throw ErroDeNegocio.naoEncontrado("Conta " + id + " nao encontrada nesta agencia.");
        }
        return conta;
    }

    public List<Conta> listarContas() {
        return contas.values().stream()
            .sorted(Comparator.comparingInt(Conta::getId))
            .toList();
    }

    // --- operacoes -------------------------------------------------------------------

    public synchronized Conta criarConta(int id, String nomeAluno, BigDecimal saldoInicial) {
        if (id < 0) {
            throw ErroDeNegocio.requisicaoInvalida("O id da conta nao pode ser negativo.");
        }
        if (AgenciaConfig.agenciaResponsavel(id) != idAgencia) {
            throw ErroDeNegocio.requisicaoInvalida(
                "Conta " + id + " nao pertence a esta agencia. Responsavel: agencia "
                    + AgenciaConfig.agenciaResponsavel(id) + ".");
        }
        if (contas.containsKey(id)) {
            throw ErroDeNegocio.conflito("Conta " + id + " ja existe.");
        }
        if (saldoInicial != null && saldoInicial.signum() < 0) {
            throw ErroDeNegocio.requisicaoInvalida("O saldo inicial nao pode ser negativo.");
        }

        int ts = relogio.eventoLocal();
        Conta conta = new Conta(id, nomeAluno, saldoInicial);
        contas.put(id, conta);
        registro.registrar("CRIAR_CONTA", ts, RegistroEventos.detalhes(
            "id", id,
            "nomeAluno", nomeAluno == null ? "" : nomeAluno,
            "saldoInicial", conta.getSaldo()
        ));
        return conta;
    }

    public synchronized Conta depositar(int id, BigDecimal valor) {
        exigirValorPositivo(valor);
        Conta conta = buscarConta(id);

        int ts = relogio.eventoLocal();
        conta.creditar(valor);
        registro.registrar("DEPOSITO", ts, RegistroEventos.detalhes(
            "id", id, "valor", valor, "novoSaldo", conta.getSaldo()
        ));
        return conta;
    }

    public synchronized Conta sacar(int id, BigDecimal valor) {
        exigirValorPositivo(valor);
        Conta conta = buscarConta(id);
        if (!conta.temSaldoPara(valor)) {
            throw ErroDeNegocio.requisicaoInvalida("Saldo insuficiente.");
        }

        int ts = relogio.eventoLocal();
        conta.debitar(valor);
        registro.registrar("SAQUE", ts, RegistroEventos.detalhes(
            "id", id, "valor", valor, "novoSaldo", conta.getSaldo()
        ));
        return conta;
    }

    // --- apoio -----------------------------------------------------------------------

    void exigirValorPositivo(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw ErroDeNegocio.requisicaoInvalida("O valor da operacao deve ser positivo.");
        }
    }
}
