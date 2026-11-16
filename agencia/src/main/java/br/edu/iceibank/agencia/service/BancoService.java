package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import br.edu.iceibank.agencia.config.LimitesConfig;
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
 * vetorial e o log de eventos. Equivale ao app.locals do exemplo em Node.
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
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final LimitesConfig limites;
    private final Map<Integer, Conta> contas = new ConcurrentHashMap<>();

    public BancoService(@Value("${agencia.id}") int idAgencia,
                        RelogioVetorial relogio,
                        RegistroEventos registro,
                        LimitesConfig limites) {
        this.idAgencia = idAgencia;
        this.relogio = relogio;
        this.registro = registro;
        this.limites = limites;
    }

    public LimitesConfig getLimites() {
        return limites;
    }

    public int getIdAgencia() {
        return idAgencia;
    }

    public RelogioVetorial getRelogio() {
        return relogio;
    }

    public RegistroEventos getRegistro() {
        return registro;
    }

    public int quantidadeDeContas() {
        return contas.size();
    }

    public record Saldo(int id, String nomeAluno, BigDecimal saldo, int[] vetorAtual) {}
    public record Pendente(String tipo, BigDecimal valor, int[] vetorConhecido, java.util.UUID operacaoId) {}
    public record Conflito(String erro, int[] vetorConhecido, int[] vetorAtual, Saldo contaAtual) {}
    public record Sincronizacao(Saldo saldo, Conflito conflito) {}
    private record Aplicada(int conta, String tipo, BigDecimal valor) {}
    private final Map<java.util.UUID, Aplicada> aplicadas = new java.util.HashMap<>();

    public synchronized Saldo consultarSaldo(int id) {
        Conta conta = buscarConta(id);
        return new Saldo(id, conta.getNomeAluno(), conta.getSaldo(), relogio.valorAtual());
    }

    public synchronized Sincronizacao sincronizar(int id, Pendente req) {
        if (req.vetorConhecido() == null || req.vetorConhecido().length != AgenciaConfig.NUMERO_AGENCIAS
                || java.util.Arrays.stream(req.vetorConhecido()).anyMatch(n -> n < 0)) {
            throw ErroDeNegocio.requisicaoInvalida("Informe um vetor conhecido válido.");
        }
        exigirValorPositivo(req.valor());
        // Fecha a janela entre comparar o vetor e alterar o saldo, inclusive durante eleição.
        synchronized (relogio) {
            Saldo atual = consultarSaldo(id);
            Aplicada anterior = req.operacaoId() == null ? null : aplicadas.get(req.operacaoId());
            if (anterior != null) {
                if (anterior.conta() != id || !anterior.tipo().equals(req.tipo())
                        || anterior.valor().compareTo(req.valor()) != 0) {
                    throw ErroDeNegocio.requisicaoInvalida("Identificador de operação já utilizado.");
                }
                return new Sincronizacao(atual, null);
            }
            if (!java.util.Arrays.equals(atual.vetorAtual(), req.vetorConhecido())) {
                return new Sincronizacao(null, new Conflito(
                    "O estado da agência mudou desde a última leitura.", req.vetorConhecido(),
                    atual.vetorAtual(), atual));
            }

            if ("DEPOSITO".equals(req.tipo())) {
                depositar(id, req.valor());
            } else if ("SAQUE".equals(req.tipo())) {
                sacar(id, req.valor());
            } else {
                throw ErroDeNegocio.requisicaoInvalida("Tipo de operação pendente não suportado.");
            }
            if (req.operacaoId() != null) {
                aplicadas.put(req.operacaoId(), new Aplicada(id, req.tipo(), req.valor()));
            }
            registro.registrar("SINCRONIZACAO_OFFLINE", relogio.valorAtual(),
                RegistroEventos.detalhes("id", id, "tipo", req.tipo(), "valor", req.valor()));
            return new Sincronizacao(consultarSaldo(id), null);
        }
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

        int[] ts = relogio.eventoLocal();
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

        int[] ts = relogio.eventoLocal();
        conta.creditar(valor);
        registro.registrar("DEPOSITO", ts, RegistroEventos.detalhes(
            "id", id, "valor", valor, "novoSaldo", conta.getSaldo()
        ));
        return conta;
    }

    public synchronized Conta sacar(int id, BigDecimal valor) {
        exigirValorPositivo(valor);
        Conta conta = buscarConta(id);

        // Funcionalidade adicional: o limite e checado ANTES do saldo, de proposito.
        // Uma tentativa de sacar 50.000 de uma conta com 100 deve ser recusada por violar
        // o limite, nao por falta de saldo - a mensagem certa muda o que a pessoa faz
        // a seguir (pedir aumento de limite x depositar dinheiro).
        if (limites.saqueAcimaDoLimite(valor)) {
            int[] tsRecusa = relogio.eventoLocal();
            registro.registrar("SAQUE_RECUSADO_LIMITE", tsRecusa, RegistroEventos.detalhes(
                "id", id, "valor", valor, "limite", limites.getLimiteSaque()
            ));
            throw ErroDeNegocio.requisicaoInvalida(
                "Valor acima do limite por saque (maximo " + limites.getLimiteSaque() + ").");
        }

        if (!conta.temSaldoPara(valor)) {
            throw ErroDeNegocio.requisicaoInvalida("Saldo insuficiente.");
        }

        int[] ts = relogio.eventoLocal();
        conta.debitar(valor);
        registro.registrar("SAQUE", ts, RegistroEventos.detalhes(
            "id", id, "valor", valor, "novoSaldo", conta.getSaldo()
        ));
        return conta;
    }

    // --- apoio -----------------------------------------------------------------------

    public void exigirValorPositivo(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw ErroDeNegocio.requisicaoInvalida("O valor da operacao deve ser positivo.");
        }
    }
}
