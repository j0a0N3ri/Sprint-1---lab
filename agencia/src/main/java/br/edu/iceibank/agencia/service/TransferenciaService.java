package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.model.Conta;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Executa transferencias locais e publica as transferencias entre agencias. */
@Service
public class TransferenciaService {

    private final BancoService banco;
    private final MensageriaService mensageria;

    public TransferenciaService(BancoService banco, MensageriaService mensageria) {
        this.banco = banco;
        this.mensageria = mensageria;
    }

    public Map<String, Object> transferir(int idOrigem, int idDestino, BigDecimal valor) {
        banco.exigirValorPositivo(valor);
        if (idOrigem == idDestino) {
            throw ErroDeNegocio.requisicaoInvalida("Origem e destino nao podem ser a mesma conta.");
        }

        if (banco.getLimites().transferenciaAcimaDoLimite(valor)) {
            int[] vetorRecusa = banco.getRelogio().eventoLocal();
            banco.getRegistro().registrar("TRANSFERENCIA_RECUSADA_LIMITE", vetorRecusa,
                RegistroEventos.detalhes(
                    "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
                    "limite", banco.getLimites().getLimiteTransferencia()
                ));
            throw ErroDeNegocio.requisicaoInvalida(
                "Valor acima do limite por transferencia (maximo "
                    + banco.getLimites().getLimiteTransferencia() + ").");
        }

        int agenciaDestino = AgenciaConfig.agenciaResponsavel(idDestino);
        UUID idTransferencia = UUID.randomUUID();
        Conta origem;

        synchronized (banco) {
            origem = banco.buscarConta(idOrigem);
            if (!origem.temSaldoPara(valor)) {
                throw ErroDeNegocio.requisicaoInvalida("Saldo insuficiente.");
            }

            int[] vetorDebito = banco.getRelogio().eventoLocal();
            origem.debitar(valor);
            banco.getRegistro().registrar("TRANSFERENCIA_DEBITO", vetorDebito,
                RegistroEventos.detalhes(
                    "idTransferencia", idTransferencia,
                    "idOrigem", idOrigem,
                    "idDestino", idDestino,
                    "valor", valor,
                    "saldoOrigem", origem.getSaldo(),
                    "agenciaDestino", agenciaDestino
                ));

            if (agenciaDestino == banco.getIdAgencia()) {
                return creditarNaMesmaAgencia(
                    origem, idTransferencia, idOrigem, idDestino, valor);
            }
        }

        int[] vetorEnvio = banco.getRelogio().aoEnviar();
        EventoCredito evento = new EventoCredito(
            idTransferencia, idOrigem, idDestino, valor, vetorEnvio, banco.getIdAgencia());

        try {
            mensageria.publicarCredito(agenciaDestino, evento);
        } catch (Exception e) {
            int[] vetorFalha = banco.getRelogio().eventoLocal();
            banco.getRegistro().registrar("TRANSFERENCIA_PUBLICACAO_FALHOU", vetorFalha,
                RegistroEventos.detalhes(
                    "idTransferencia", idTransferencia,
                    "idOrigem", idOrigem,
                    "idDestino", idDestino,
                    "valor", valor,
                    "erro", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    "debitoRevertido", false
                ));
            throw new ErroDeNegocio(HttpStatus.BAD_GATEWAY,
                "Nao foi possivel publicar a transferencia. Debito ja aplicado.");
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", "Transferencia publicada para a agencia de destino.");
        resposta.put("tipo", "ENTRE_AGENCIAS");
        resposta.put("status", "PUBLICADA");
        resposta.put("idTransferencia", idTransferencia);
        resposta.put("agenciaDestino", agenciaDestino);
        resposta.put("timestampVetorial", vetorEnvio);
        resposta.put("saldoOrigem", origem.getSaldo());
        return resposta;
    }

    private Map<String, Object> creditarNaMesmaAgencia(Conta origem, UUID idTransferencia,
                                                        int idOrigem, int idDestino,
                                                        BigDecimal valor) {
        Conta destino;
        try {
            destino = banco.buscarConta(idDestino);
        } catch (ErroDeNegocio e) {
            origem.creditar(valor);
            throw ErroDeNegocio.naoEncontrado("Conta de destino " + idDestino + " nao encontrada.");
        }

        int[] vetorCredito = banco.getRelogio().eventoLocal();
        destino.creditar(valor);
        banco.getRegistro().registrar("TRANSFERENCIA_CREDITO", vetorCredito,
            RegistroEventos.detalhes(
                "idTransferencia", idTransferencia,
                "idOrigem", idOrigem,
                "idDestino", idDestino,
                "valor", valor,
                "saldoDestino", destino.getSaldo()
            ));

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", "Transferencia concluida (mesma agencia).");
        resposta.put("tipo", "LOCAL");
        resposta.put("status", "CONCLUIDA");
        resposta.put("idTransferencia", idTransferencia);
        resposta.put("timestampVetorial", vetorCredito);
        resposta.put("saldoOrigem", origem.getSaldo());
        resposta.put("saldoDestino", destino.getSaldo());
        return resposta;
    }
}
