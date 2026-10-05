package br.edu.iceibank.agencia.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declara a topologia RabbitMQ usada por cada instancia da agencia. */
@Configuration
public class MensageriaConfig {

    public static final String EXCHANGE_EVENTOS = "iceibank.eventos";
    public static final String EXCHANGE_NAO_PROCESSADOS = "iceibank.eventos.dlx";

    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }

    @Bean
    public DirectExchange exchangeNaoProcessados() {
        return new DirectExchange(EXCHANGE_NAO_PROCESSADOS, true, false);
    }

    @Bean("nomeFilaAgencia")
    public String nomeFilaAgencia(@Value("${agencia.id}") int idAgencia) {
        return "fila-agencia-" + idAgencia;
    }

    @Bean
    public Queue filaAgencia(@Value("${agencia.id}") int idAgencia) {
        return QueueBuilder.durable("fila-agencia-" + idAgencia)
            .deadLetterExchange(EXCHANGE_NAO_PROCESSADOS)
            .deadLetterRoutingKey("agencia." + idAgencia + ".credito-falhou")
            .build();
    }

    @Bean
    public Binding vinculoFilaAgencia(@Qualifier("filaAgencia") Queue filaAgencia,
                                      TopicExchange exchangeEventos,
                                      @Value("${agencia.id}") int idAgencia) {
        return BindingBuilder.bind(filaAgencia)
            .to(exchangeEventos)
            .with("agencia." + idAgencia + ".creditar");
    }

    @Bean
    public Queue filaNaoProcessados(@Value("${agencia.id}") int idAgencia) {
        return QueueBuilder.durable("fila-agencia-" + idAgencia + ".nao-processadas").build();
    }

    @Bean
    public Binding vinculoFilaNaoProcessados(
                                              @Qualifier("filaNaoProcessados") Queue filaNaoProcessados,
                                              DirectExchange exchangeNaoProcessados,
                                              @Value("${agencia.id}") int idAgencia) {
        return BindingBuilder.bind(filaNaoProcessados)
            .to(exchangeNaoProcessados)
            .with("agencia." + idAgencia + ".credito-falhou");
    }
}
