package br.edu.iceibank.agencia;

import br.edu.iceibank.agencia.config.AgenciaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ponto de entrada da agencia.
 *
 * O MESMO codigo roda tres vezes, com identidades diferentes: quem decide qual agencia este
 * processo e a variavel de ambiente AGENCIA_ID (0, 1 ou 2). Dela saem a porta (4000 + OFFSET
 * + id) e o nome do arquivo de log. E o que torna as tres agencias nos independentes de um
 * sistema distribuido, e nao tres copias do mesmo servidor.
 *
 *   AGENCIA_ID=0 mvn spring-boot:run
 */
@SpringBootApplication
public class AgenciaApplication {

	public static void main(String[] args) {
		int idAgencia = lerIdDaAgencia();

		AgenciaConfig.Agencia agencia = AgenciaConfig.AGENCIAS.stream()
			.filter(a -> a.id() == idAgencia)
			.findFirst()
			.orElse(null);

		if (agencia == null) {
			System.err.println("Agencia " + idAgencia + " nao configurada em AgenciaConfig.");
			System.exit(1);
		}

		int porta = AgenciaConfig.PORTA_BASE + idAgencia;

		// Os valores entram como argumento de linha de comando, e nao via
		// setDefaultProperties: default properties tem precedencia MENOR que o
		// application.properties, entao as tres agencias herdariam a mesma porta 4000 de la
		// e so a primeira conseguiria subir. Argumento de linha de comando ganha do arquivo.
		List<String> argumentos = new ArrayList<>(Arrays.asList(args));
		argumentos.add("--agencia.id=" + idAgencia);
		argumentos.add("--server.port=" + porta);

		SpringApplication app = new SpringApplication(AgenciaApplication.class);
		app.run(argumentos.toArray(new String[0]));

		System.out.println("[Agencia " + idAgencia + "] ouvindo em " + agencia.url());
	}

	private static int lerIdDaAgencia() {
		String valor = System.getenv("AGENCIA_ID");
		if (valor == null || valor.isBlank()) {
			valor = System.getProperty("agencia.id", "0");
		}
		try {
			return Integer.parseInt(valor.trim());
		} catch (NumberFormatException e) {
			System.err.println("AGENCIA_ID invalido: " + valor);
			System.exit(1);
			return -1;
		}
	}
}
