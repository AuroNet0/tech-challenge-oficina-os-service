package com.oficina.os.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.application.port.out.SagaRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.application.saga.CommandEnvelope;
import com.oficina.os.application.saga.SagaEventHandler;
import com.oficina.os.application.saga.SagaEventType;
import com.oficina.os.application.usecase.OrdemServicoUseCase;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.SagaInstance;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = { "oficina.outbox.publisher.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false" })
class SagaFailureIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("oficina_os_saga_failure_test")
			.withUsername("oficina_os")
			.withPassword("oficina_os");

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private OrdemServicoUseCase orders;
	@Autowired
	private ClienteRepositoryPort clients;
	@Autowired
	private VeiculoRepositoryPort vehicles;
	@Autowired
	private FilialRepositoryPort branches;
	@Autowired
	private SagaRepositoryPort sagas;
	@Autowired
	private SagaEventHandler handler;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ObjectMapper mapper;

	@Test
	void incompatibleFailuresOnlyPersistRejectedInboxAndPreserveCommittedBusinessRows() throws Exception {
		Cliente client = clients.save(Cliente.novo("Maria", "52998224725", null, null));
		Veiculo vehicle = vehicles.save(Veiculo.novo(client.getId(), "SAG1234", "Fiat", "Uno", 2020));
		Filial branch = branches.save(Filial.nova("Centro", "SAGA-FAILURE", "Sao Paulo", "SP"));
		var order = orders.abrir(new AbrirOrdemServicoCommand(client.getId(), vehicle.getId(), branch.getId(), null));
		SagaInstance saga = sagas.findByOrdemServicoId(order.getId()).orElseThrow();

		assertFailureRejectedWithoutBusinessChanges(saga, SagaEventType.QUOTE_CREATION_FAILED);
		handler.handle(event(saga, SagaEventType.DIAGNOSTIC_STARTED, Map.of()));
		handler.handle(event(saga, SagaEventType.DIAGNOSTIC_COMPLETED, Map.of("items", List.of(Map.of(
				"itemId", "1", "descricao", "Troca de oleo", "tipo", "SERVICO", "quantidade", "1",
				"unidadePreco", "UNIDADE", "precoPrevisto", "120.00")))));

		assertFailureRejectedWithoutBusinessChanges(saga, SagaEventType.DIAGNOSTIC_FAILED);
		handler.handle(event(saga, SagaEventType.QUOTE_CREATED, Map.of("quoteId", "Q-1")));

		assertFailureRejectedWithoutBusinessChanges(saga, SagaEventType.DIAGNOSTIC_FAILED);
		assertFailureRejectedWithoutBusinessChanges(saga, SagaEventType.QUOTE_CREATION_FAILED);
		assertThat(jdbc.queryForObject("SELECT status FROM ordens_servico WHERE id = ?", String.class, order.getId()))
				.isEqualTo("AGUARDANDO_APROVACAO");
		assertThat(jdbc.queryForObject("SELECT status FROM saga_instances WHERE id = ?", String.class, saga.getId()))
				.isEqualTo("WAITING_APPROVAL");
	}

	private void assertFailureRejectedWithoutBusinessChanges(SagaInstance saga, String eventType) throws Exception {
		var before = businessRows(saga);
		String raw = event(saga, eventType, Map.of("reason", "falha incompativel"));
		UUID eventId = UUID.fromString(mapper.readTree(raw).get("eventId").asText());

		handler.handle(raw);

		assertThat(businessRows(saga)).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT status FROM inbox_messages WHERE event_id = ?", String.class, eventId))
				.isEqualTo("REJECTED");
		var rejectedInbox = jdbc.queryForList("SELECT * FROM inbox_messages WHERE saga_id = ? ORDER BY event_id", saga.getId());

		handler.handle(raw);

		assertThat(businessRows(saga)).isEqualTo(before);
		assertThat(jdbc.queryForList("SELECT * FROM inbox_messages WHERE saga_id = ? ORDER BY event_id", saga.getId()))
				.isEqualTo(rejectedInbox);

		String late = event(saga, eventType, Map.of("reason", "falha incompativel"));
		UUID lateId = UUID.fromString(mapper.readTree(late).get("eventId").asText());
		assertThat(lateId).isNotEqualTo(eventId);
		handler.handle(late);

		assertThat(businessRows(saga)).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT status FROM inbox_messages WHERE event_id = ?", String.class, lateId))
				.isEqualTo("REJECTED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox_messages WHERE saga_id = ?", Integer.class, saga.getId()))
				.isEqualTo(rejectedInbox.size() + 1);
	}

	private Map<String, List<Map<String, Object>>> businessRows(SagaInstance saga) {
		return Map.of(
				"os", jdbc.queryForList("SELECT * FROM ordens_servico WHERE id = ?", saga.getOrdemServicoId()),
				"saga", jdbc.queryForList("SELECT * FROM saga_instances WHERE id = ?", saga.getId()),
				"history", jdbc.queryForList("SELECT * FROM historico_status WHERE ordem_servico_id = ? ORDER BY id", saga.getOrdemServicoId()),
				"outbox", jdbc.queryForList("SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY id", saga.getOrdemServicoId()));
	}

	private String event(SagaInstance saga, String eventType, Object payload) throws Exception {
		return mapper.writeValueAsString(new CommandEnvelope<>(UUID.randomUUID(), eventType, Instant.now(),
				saga.getCorrelationId(), saga.getId(), saga.getOrdemServicoId(), "test", payload));
	}
}
