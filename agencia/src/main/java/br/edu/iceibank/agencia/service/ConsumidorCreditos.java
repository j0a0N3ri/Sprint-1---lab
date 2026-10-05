package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.exception.ErroDeNegocio;
import br.edu.iceibank.agencia.model.Conta;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Processa os creditos remotos sem depender de uma chamada HTTP entre agencias. */
@Service
public class ConsumidorCreditos {

    private final BancoService banco;
    private final ObjectMapper json;

    public ConsumidorCreditos(BancoService banco, ObjectMapper json) {
        this.banco = banco;
        this.json = json;
    }

    @RabbitListener(queues = "#{@nomeFilaAgencia}")
    public void consumir(String corpo) {
        EventoCredito evento;
        try {
            evento = json.readValue(corpo, EventoCredito.class);
        } catch (Exception e) {
            int[] vetor = banco.getRelogio().eventoLocal();
            banco.getRegistro().registrar("MENSAGEM_INVALIDA", vetor,
                RegistroEventos.detalhes("motivo", e.getMessage() == null ? "JSON invalido" : e.getMessage()));
            throw new AmqpRejectAndDontRequeueException("Mensagem de credito invalida", e);
        }

        synchronized (banco) {
            int[] vetor = banco.getRelogio().aoReceber(evento.vetorEnvio());
            try {
                Conta conta = banco.buscarConta(evento.idContaDestino());
                conta.creditar(evento.valor());
                banco.getRegistro().registrar("TRANSFERENCIA_CREDITO_REMOTO", vetor,
                    RegistroEventos.detalhes(
                        "idTransferencia", evento.idTransferencia(),
                        "idContaOrigem", evento.idContaOrigem(),
                        "idContaDestino", evento.idContaDestino(),
                        "valor", evento.valor(),
                        "origemAgencia", evento.origemAgencia(),
                        "vetorRecebido", evento.vetorEnvio(),
                        "novoSaldo", conta.getSaldo()
                    ));
            } catch (ErroDeNegocio e) {
                banco.getRegistro().registrar("CREDITO_REMOTO_FALHOU", vetor,
                    RegistroEventos.detalhes(
                        "idTransferencia", evento.idTransferencia(),
                        "idContaDestino", evento.idContaDestino(),
                        "valor", evento.valor(),
                        "origemAgencia", evento.origemAgencia(),
                        "motivo", "conta nao encontrada",
                        "destino", "fila-agencia-" + banco.getIdAgencia() + ".nao-processadas"
                    ));
                throw new AmqpRejectAndDontRequeueException(
                    "Credito nao processado: conta de destino inexistente", e);
            }
        }
    }
}
