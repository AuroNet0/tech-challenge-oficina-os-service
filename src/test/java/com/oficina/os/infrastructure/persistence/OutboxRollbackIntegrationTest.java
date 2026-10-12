package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.application.usecase.OrdemServicoUseCase;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "oficina.outbox.publisher.enabled=false")
class OutboxRollbackIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("oficina_os_outbox_rollback_test")
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

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void rollsBackOrderAndHistoryWhenOutboxPersistenceFails() {
		Cliente cliente = clientes.save(Cliente.novo("Maria", "52998224725", null, null));
		Veiculo veiculo = veiculos.save(Veiculo.novo(cliente.getId(), "OXR1234", "Fiat", "Uno", 2020));
		Filial filial = filiais.save(Filial.nova("Centro", "SP-OXR", "Sao Paulo", "SP"));

		assertThatThrownBy(() -> useCase.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId(),
				filial.getId(), null))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("forced outbox failure");

		assertThat(ordensServico.findByClienteId(cliente.getId())).isEmpty();
		Integer historyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM historico_status", Integer.class);
		Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
		assertThat(historyCount).isZero();
		assertThat(outboxCount).isZero();
	}

	@TestConfiguration
	static class FailingOutboxConfig {

		@Bean
		@Primary
		OutboxEventRepositoryPort failingOutboxRepository(
				@Qualifier("outboxEventRepositoryAdapter") OutboxEventRepositoryPort delegate) {
			return new OutboxEventRepositoryPort() {
				@Override
				public OutboxEvent save(OutboxEvent event) {
					delegate.save(event);
					throw new IllegalStateException("forced outbox failure");
				}

				@Override
				public List<OutboxEvent> acquirePending(int batchSize, Instant now, Duration leaseDuration) {
					return delegate.acquirePending(batchSize, now, leaseDuration);
				}

				@Override
				public void markPublished(UUID eventId, Instant publishedAt) {
					delegate.markPublished(eventId, publishedAt);
				}

				@Override
				public void markFailedAttempt(UUID eventId, Instant now, int maxAttempts, Duration initialBackoff,
						Duration maxBackoff, String error) {
					delegate.markFailedAttempt(eventId, now, maxAttempts, initialBackoff, maxBackoff, error);
				}

				@Override
				public Optional<OutboxEvent> findById(UUID eventId) {
					return delegate.findById(eventId);
				}
			};
		}
	}
}
