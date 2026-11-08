package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.application.usecase.OrdemServicoUseCase;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.HistoricoStatus;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "oficina.outbox.publisher.enabled=false")
class OrdemServicoRollbackIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("oficina_os_rollback_test")
			.withUsername("oficina_os")
			.withPassword("oficina_os");

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private OrdemServicoUseCase useCase;

	@Autowired
	private ClienteRepositoryPort clientes;

	@Autowired
	private VeiculoRepositoryPort veiculos;

	@Autowired
	private FilialRepositoryPort filiais;

	@Autowired
	private OrdemServicoRepositoryPort ordensServico;

	@Test
	void rollsBackOrderWhenInitialHistoryFails() {
		Cliente cliente = clientes.save(Cliente.novo("Maria", "52998224725", null, null));
		Veiculo veiculo = veiculos.save(Veiculo.novo(cliente.getId(), "MNO1234", "Fiat", "Uno", 2020));
		Filial filial = filiais.save(Filial.nova("Centro", "SP-RB", "Sao Paulo", "SP"));

		assertThatThrownBy(() -> useCase.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId(), filial.getId(), null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("forced history failure");

		assertThat(ordensServico.findByClienteId(cliente.getId())).isEmpty();
	}

	@TestConfiguration
	static class FailingHistoryConfig {

		@Bean
		@Primary
		HistoricoStatusRepositoryPort failingHistoricoStatusRepository(
				@Qualifier("historicoStatusRepositoryAdapter") HistoricoStatusRepositoryPort delegate) {
			return new HistoricoStatusRepositoryPort() {
				@Override
				public HistoricoStatus save(HistoricoStatus historicoStatus) {
					delegate.save(historicoStatus);
					throw new IllegalStateException("forced history failure");
				}

				@Override
				public List<HistoricoStatus> findByOrdemServicoId(UUID ordemServicoId) {
					return delegate.findByOrdemServicoId(ordemServicoId);
				}
			};
		}
	}
}
