package br.edu.iceibank.agencia.config;

import br.edu.iceibank.agencia.service.RegistroEventos;
import br.edu.iceibank.agencia.service.RelogioVetorial;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publica como beans as duas pecas de estado da agencia. Sao singletons: existe UM relogio
 * vetorial por processo (o processo e que e o "no" do sistema distribuido, nao a requisicao)
 * e UM arquivo de log por agencia.
 */
@Configuration
public class BeansDaAgencia {

    @Bean
    public RelogioVetorial relogioVetorial(@Value("${agencia.id}") int idAgencia) {
        return new RelogioVetorial(idAgencia, AgenciaConfig.NUMERO_AGENCIAS);
    }

    @Bean
    public RegistroEventos registroEventos(@Value("${agencia.id}") int idAgencia) {
        return new RegistroEventos("agencia-" + idAgencia);
    }
}
