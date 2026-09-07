package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import br.edu.iceibank.agencia.controller.dto.CreditoRemotoRequest;
import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.model.Conta;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transferencias: o caso local (mesma agencia) e o caso entre agencias (mensagem REST).
 *
 * O debito e SEMPRE local, porque esta agencia e a dona da conta de origem - a particao
 * garante isso. O que muda entre os dois casos e so como o credito chega no destino.
 */
@Service
public class TransferenciaService {

    private final BancoService banco;
    private final ClienteAgencias cliente;

    public TransferenciaService(BancoService banco, ClienteAgencias cliente) {
        this.banco = banco;
        this.cliente = cliente;
    }

    public Map<String, Object> transferir(int idOrigem, int idDestino, BigDecimal valor) {
        banco.exigirValorPositivo(valor);
        if (idOrigem == idDestino) {
            throw ErroDeNegocio.requisicaoInvalida("Origem e destino nao podem ser a mesma conta.");
        }

        int agenciaDestino = AgenciaConfig.agenciaResponsavel(idDestino);
        int tsEnvio;

        // O bloco sincronizado usa o MESMO lock dos metodos synchronized do BancoService
        // (o proprio objeto banco). Assim, debito e credito local acontecem como uma unidade:
        // nenhum saque concorrente enxerga o estado no meio do caminho.
        synchronized (banco) {
            Conta origem = banco.buscarConta(idOrigem);
            if (!origem.temSaldoPara(valor)) {
                throw ErroDeNegocio.requisicaoInvalida("Saldo insuficiente.");
            }

            int tsDebito = banco.getRelogio().eventoLocal();
            origem.debitar(valor);
            banco.getRegistro().registrar("TRANSFERENCIA_DEBITO", tsDebito, RegistroEventos.detalhes(
                "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
                "saldoOrigem", origem.getSaldo(), "agenciaDestino", agenciaDestino
            ));

            if (agenciaDestino == banco.getIdAgencia()) {
                return creditarNaMesmaAgencia(origem, idOrigem, idDestino, valor);
            }

            // Regra 2 de Lamport: ao ENVIAR uma mensagem, incrementa o contador e anexa
            // o valor a mensagem. O ts vai no corpo do POST logo abaixo.
            tsEnvio = banco.getRelogio().aoEnviar();
        }

        // A chamada de rede acontece FORA do lock de proposito: segurar o lock durante a
        // ida e volta HTTP travaria a agencia inteira (todo saque, deposito e consulta)
        // enquanto se espera outra maquina responder.
        String urlDestino = AgenciaConfig.AGENCIAS.stream()
            .filter(a -> a.id() == agenciaDestino)
            .findFirst()
            .orElseThrow(() -> ErroDeNegocio.requisicaoInvalida("Agencia de destino desconhecida."))
            .url();

        try {
            cliente.creditarRemoto(urlDestino, idDestino,
                new CreditoRemotoRequest(valor, tsEnvio, banco.getIdAgencia()));
        } catch (Exception e) {
            return falhaEntreAgencias(idOrigem, idDestino, valor, e);
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", "Transferencia concluida (entre agencias).");
        resposta.put("tipo", "ENTRE_AGENCIAS");
        resposta.put("agenciaDestino", agenciaDestino);
        resposta.put("timestampLamport", tsEnvio);
        resposta.put("saldoOrigem", banco.buscarConta(idOrigem).getSaldo());
        return resposta;
    }

    private Map<String, Object> creditarNaMesmaAgencia(Conta origem, int idOrigem, int idDestino, BigDecimal valor) {
        Conta destino;
        try {
            destino = banco.buscarConta(idDestino);
        } catch (ErroDeNegocio e) {
            // Aqui o estorno E possivel e correto: as duas contas estao nesta agencia, sob o
            // mesmo lock, entao "debitar e desfazer" e uma operacao local e atomica. E o
            // contraste com o caso entre agencias, onde nao existe essa garantia.
            origem.creditar(valor);
            throw ErroDeNegocio.naoEncontrado("Conta de destino " + idDestino + " nao encontrada.");
        }

        int tsCredito = banco.getRelogio().eventoLocal();
        destino.creditar(valor);
        banco.getRegistro().registrar("TRANSFERENCIA_CREDITO", tsCredito, RegistroEventos.detalhes(
            "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
            "saldoDestino", destino.getSaldo()
        ));

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", "Transferencia concluida (mesma agencia).");
        resposta.put("tipo", "LOCAL");
        resposta.put("timestampLamport", tsCredito);
        resposta.put("saldoOrigem", origem.getSaldo());
        resposta.put("saldoDestino", destino.getSaldo());
        return resposta;
    }

    /**
     * LIMITACAO CONHECIDA (intencional neste sprint).
     *
     * O debito ja foi aplicado antes da chamada de rede. Se a agencia de destino nao responde,
     * o dinheiro "desaparece": saiu da origem e nunca chegou no destino. O sistema NAO reverte
     * automaticamente - so registra a inconsistencia no log, com o valor exato e as duas contas,
     * para que ela seja auditavel.
     *
     * Reverter aqui de forma ingenua seria pior do que nao reverter: nao ha como saber se a
     * agencia de destino recebeu a mensagem e falhou ao responder (nesse caso o credito
     * aconteceu, e o estorno criaria dinheiro do nada) ou se nunca a recebeu. Resolver isso de
     * verdade exige um protocolo de transacao distribuida - 2PC ou Saga - que e o assunto do
     * Sprint 4.
     */
    private Map<String, Object> falhaEntreAgencias(int idOrigem, int idDestino, BigDecimal valor, Exception causa) {
        int tsFalha = banco.getRelogio().eventoLocal();
        banco.getRegistro().registrar("TRANSFERENCIA_FALHOU", tsFalha, RegistroEventos.detalhes(
            "idOrigem", idOrigem,
            "idDestino", idDestino,
            "valor", valor,
            "erro", causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage(),
            "debitoRevertido", false
        ));
        throw new ErroDeNegocio(HttpStatus.BAD_GATEWAY,
            "Falha ao contatar a agencia de destino. Debito ja aplicado - inconsistencia conhecida (ver Sprint 4).");
    }

    /**
     * Ponta receptora da mensagem entre agencias.
     *
     * O relogio e ajustado pela regra 3 (max(local, recebido) + 1) ANTES de qualquer checagem
     * de conta: receber a mensagem ja e um evento no processo, tenha ela sido util ou nao.
     * Se o ajuste dependesse do sucesso, dois eventos causalmente ligados poderiam ficar com
     * a ordem invertida no log.
     */
    public Map<String, Object> creditarRemoto(int idConta, CreditoRemotoRequest req) {
        if (req.timestampLamport() == null) {
            throw ErroDeNegocio.requisicaoInvalida("timestampLamport e obrigatorio no credito remoto.");
        }
        banco.exigirValorPositivo(req.valor());

        synchronized (banco) {
            int ts = banco.getRelogio().aoReceber(req.timestampLamport());

            Conta conta = banco.buscarConta(idConta);
            conta.creditar(req.valor());
            banco.getRegistro().registrar("TRANSFERENCIA_CREDITO_REMOTO", ts, RegistroEventos.detalhes(
                "idConta", idConta,
                "valor", req.valor(),
                "origemAgencia", req.origemAgencia(),
                "timestampRecebido", req.timestampLamport(),
                "novoSaldo", conta.getSaldo()
            ));

            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("mensagem", "Credito remoto aplicado.");
            resposta.put("timestampLamport", ts);
            resposta.put("saldoAtual", conta.getSaldo());
            return resposta;
        }
    }
}
