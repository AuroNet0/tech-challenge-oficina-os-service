package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.application.usecase.OrdemServicoUseCase;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.OutboxStatus;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "oficina.outbox.publisher.enabled=false")
class OutboxPersistenceIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("oficina_os_outbox_test")
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
	private HistoricoStatusRepositoryPort historicos;

	@Autowired
	private OrdemServicoRepositoryPort ordensServico;

	@Autowired
	private OutboxEventRepositoryPort outboxEvents;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayRunsV3() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name = 'outbox_events'
				""", Integer.class);
		Integer migrationCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM flyway_schema_history
				WHERE script = 'V3__create_outbox_events.sql'
				  AND success = true
				""", Integer.class);

		assertThat(tableCount).isEqualTo(1);
		assertThat(migrationCount).isEqualTo(1);
	}

	@Test
	void openingOrderPersistsOrderHistoryAndOutboxAtomically() {
		Fixture fixture = fixture("OSB1234", "SP-OUTBOX");

		var ordemServico = useCase.abrir(new AbrirOrdemServicoCommand(fixture.cliente().getId(),
				fixture.veiculo().getId(), fixture.filial().getId(), "Barulho no motor"));

		assertThat(ordensServico.findById(ordemServico.getId())).isPresent();
		assertThat(historicos.findByOrdemServicoId(ordemServico.getId())).hasSize(1);
		Integer outboxCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, ordemServico.getId());
		assertThat(outboxCount).isEqualTo(1);
		String eventType = jdbcTemplate.queryForObject(
				"SELECT payload ->> 'eventType' FROM outbox_events WHERE aggregate_id = ?", String.class,
				ordemServico.getId());
		assertThat(eventType).isEqualTo(OutboxEvent.DIAGNOSTIC_START_V1);
	}

	@Test
	void leasePreventsConcurrentAcquisitionAndExpiredProcessingIsRecovered() {
		jdbcTemplate.update("DELETE FROM outbox_events");
		OutboxEvent first = outboxEvents.save(OutboxEvent.pending(java.util.UUID.randomUUID(),
				OutboxEvent.ORDEM_SERVICO, OutboxEvent.DIAGNOSTIC_START_V1, "{\"eventId\":\"one\"}", null));
		OutboxEvent second = outboxEvents.save(OutboxEvent.pending(java.util.UUID.randomUUID(),
				OutboxEvent.ORDEM_SERVICO, OutboxEvent.DIAGNOSTIC_START_V1, "{\"eventId\":\"two\"}", null));

		List<OutboxEvent> acquired = outboxEvents.acquirePending(1, Instant.now(), Duration.ofSeconds(30));
		List<OutboxEvent> concurrentlyAcquired = outboxEvents.acquirePending(1, Instant.now(), Duration.ofSeconds(30));

		assertThat(acquired).hasSize(1);
		assertThat(concurrentlyAcquired).extracting(OutboxEvent::getId).containsExactly(second.getId());
		assertThat(concurrentlyAcquired).extracting(OutboxEvent::getId).doesNotContain(first.getId());

		jdbcTemplate.update("UPDATE outbox_events SET locked_until = now() - interval '1 second' WHERE id = ?",
				acquired.getFirst().getId());
		List<OutboxEvent> recovered = outboxEvents.acquirePending(10, Instant.now(), Duration.ofSeconds(30));

		assertThat(recovered).extracting(OutboxEvent::getId).contains(acquired.getFirst().getId());
	}

	@Test
	void failedAttemptsEventuallyMarkFailed() {
		jdbcTemplate.update("DELETE FROM outbox_events");
		OutboxEvent event = outboxEvents.save(OutboxEvent.pending(java.util.UUID.randomUUID(),
				OutboxEvent.ORDEM_SERVICO, OutboxEvent.DIAGNOSTIC_START_V1, "{\"eventId\":\"fail\"}", null));

		outboxEvents.markFailedAttempt(event.getId(), Instant.now(), 2, Duration.ofMillis(1), Duration.ofSeconds(1),
				"temporary");
		OutboxEvent retry = outboxEvents.findById(event.getId()).orElseThrow();
		outboxEvents.markFailedAttempt(event.getId(), Instant.now(), 2, Duration.ofMillis(1), Duration.ofSeconds(1),
				"permanent");
		OutboxEvent failed = outboxEvents.findById(event.getId()).orElseThrow();

		assertThat(retry.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(retry.getNextAttemptAt()).isNotNull();
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(failed.getNextAttemptAt()).isNull();
	}

	private Fixture fixture(String placa, String codigoFilial) {
		Cliente cliente = clientes.save(Cliente.novo("Maria", "52998224725", null, null));
		Veiculo veiculo = veiculos.save(Veiculo.novo(cliente.getId(), placa, "Fiat", "Uno", 2020));
		Filial filial = filiais.save(Filial.nova("Centro", codigoFilial, "Sao Paulo", "SP"));
		return new Fixture(cliente, veiculo, filial);
	}

	private record Fixture(Cliente cliente, Veiculo veiculo, Filial filial) {
	}
}
