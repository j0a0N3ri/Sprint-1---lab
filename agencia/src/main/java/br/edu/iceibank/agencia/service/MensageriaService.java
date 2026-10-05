package br.edu.iceibank.agencia.service;

import br.edu.iceibank.agencia.config.MensageriaConfig;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Unico ponto de publicacao de eventos no RabbitMQ. */
@Service
public class MensageriaService {

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public MensageriaService(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = rabbit;
        this.json = json;
    }

    public void publicarCredito(int agenciaDestino, EventoCredito evento) {
        String routingKey = "agencia." + agenciaDestino + ".creditar";
        String corpo = json.writeValueAsString(evento);
        rabbit.convertAndSend(MensageriaConfig.EXCHANGE_EVENTOS, routingKey, corpo, mensagem -> {
            mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            mensagem.getMessageProperties().setMessageId(evento.idTransferencia().toString());
            return mensagem;
        });
    }
}
