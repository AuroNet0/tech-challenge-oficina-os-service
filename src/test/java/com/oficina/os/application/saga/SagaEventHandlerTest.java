package com.oficina.os.application.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.application.port.out.InboxRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoSearchCriteria;
import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.application.port.out.PageResult;
import com.oficina.os.application.port.out.SagaRepositoryPort;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.HistoricoStatus;
import com.oficina.os.domain.InboxMessage;
import com.oficina.os.domain.InboxStatus;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OrdemServicoStatus;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.SagaInstance;
import com.oficina.os.domain.SagaStatus;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SagaEventHandlerTest {

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	private final SagaMessageSerializer serializer = new SagaMessageSerializer(objectMapper);
	private final InMemoryInbox inbox = new InMemoryInbox();
	private final InMemorySagaRepository sagas = new InMemorySagaRepository();
	private final InMemoryOrdemServicoRepository ordens = new InMemoryOrdemServicoRepository();
	private final InMemoryHistoricoRepository historicos = new InMemoryHistoricoRepository();
	private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
	private final SagaEventHandler handler = new SagaEventHandler(inbox, sagas, ordens, historicos, outbox,
			serializer, new SagaStateMachine());

	@Test
	void processesSuccessfulSagaFlowAndKeepsDeliveryAdministrative() {
		Fixture fixture = fixture();

		handler.handle(event(SagaEventType.DIAGNOSTIC_STARTED, fixture, Map.of()));
		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.EM_DIAGNOSTICO);

		handler.handle(event(SagaEventType.DIAGNOSTIC_COMPLETED, fixture, Map.of("items",
				List.of(Map.of("itemId", "1", "descricao", "Troca de oleo", "precoPrevisto", "120.00")))));
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.WAITING_QUOTE);
		assertThat(outbox.events).extracting(OutboxEvent::getEventType).contains(SagaEventType.QUOTE_CREATE_COMMAND);

		handler.handle(event(SagaEventType.QUOTE_CREATED, fixture, Map.of("quoteId", "Q-1")));
		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.AGUARDANDO_APROVACAO);

		handler.handle(event(SagaEventType.QUOTE_APPROVED, fixture, Map.of()));
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.WAITING_REPAIR);
		assertThat(outbox.events).extracting(OutboxEvent::getEventType).contains(SagaEventType.REPAIR_START_COMMAND);

		handler.handle(event(SagaEventType.REPAIR_STARTED, fixture, Map.of()));
		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.EM_EXECUCAO);

		handler.handle(event(SagaEventType.REPAIR_COMPLETED, fixture, Map.of("repairId", "R-1")));
		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.AGUARDANDO_PAGAMENTO);
		assertThat(outbox.events).extracting(OutboxEvent::getEventType).contains(SagaEventType.PAYMENT_REQUEST_COMMAND);

		handler.handle(event(SagaEventType.PAYMENT_CONFIRMED, fixture, Map.of("paymentId", "P-1")));
		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.FINALIZADA);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.COMPLETED);
	}

	@Test
	void rejectsOutOfOrderEventAndRecordsInboxAsRejected() {
		Fixture fixture = fixture();
		String raw = event(SagaEventType.QUOTE_CREATED, fixture, Map.of("quoteId", "Q-1"));

		handler.handle(raw);

		assertThat(inbox.messages.values()).singleElement()
				.extracting(InboxMessage::getStatus)
				.isEqualTo(InboxStatus.REJECTED);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.WAITING_DIAGNOSTIC);
	}

	@Test
	void ignoresDuplicateProcessedEvent() {
		Fixture fixture = fixture();
		String raw = event(SagaEventType.DIAGNOSTIC_STARTED, fixture, Map.of());

		handler.handle(raw);
		handler.handle(raw);

		assertThat(historicos.history).hasSize(1);
		assertThat(inbox.messages.values()).singleElement()
				.extracting(InboxMessage::getStatus)
				.isEqualTo(InboxStatus.PROCESSED);
	}

	@Test
	void compensatesDiagnosticFailureBeforePhysicalExecution() {
		Fixture fixture = fixture();

		handler.handle(event(SagaEventType.DIAGNOSTIC_FAILED, fixture, Map.of("reason", "sem tecnico")));

		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.CANCELADA);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.COMPENSATED);
	}

	@Test
	void paymentFailureKeepsOrderWaitingPaymentAndFailsSaga() {
		Fixture fixture = fixtureAtPayment();

		handler.handle(event(SagaEventType.PAYMENT_FAILED, fixture, Map.of("reason", "cartao recusado")));

		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.AGUARDANDO_PAGAMENTO);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.FAILED);
	}

	private Fixture fixture() {
		Cliente cliente = Cliente.novo("Maria", "52998224725", null, null);
		Veiculo veiculo = Veiculo.novo(cliente.getId(), "ABC1234", "Fiat", "Uno", 2020);
		Filial filial = Filial.nova("Centro", "SP01", "Sao Paulo", "SP");
		OrdemServico os = OrdemServico.abrir(cliente, veiculo, filial, null);
		SagaInstance saga = SagaInstance.start(os.getId(), UUID.randomUUID());
		ordens.save(os);
		sagas.save(saga);
		return new Fixture(os, saga);
	}

	private Fixture fixtureAtPayment() {
		Fixture fixture = fixture();
		UUID correlationId = fixture.saga().getCorrelationId();
		fixture.os().iniciarDiagnostico("diag", correlationId);
		fixture.os().aguardarAprovacao("quote", correlationId);
		fixture.os().iniciarExecucao("repair", correlationId);
		fixture.os().aguardarPagamento("payment", correlationId);
		fixture.saga().moveTo(SagaStatus.WAITING_PAYMENT, com.oficina.os.domain.SagaStep.PAYMENT);
		ordens.save(fixture.os());
		sagas.save(fixture.saga());
		return fixture;
	}

	private String event(String type, Fixture fixture, Object payload) {
		try {
			return objectMapper.writeValueAsString(new CommandEnvelope<>(UUID.randomUUID(), type, Instant.now(),
					fixture.saga().getCorrelationId(), fixture.saga().getId(), fixture.os().getId(), "test", payload));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record Fixture(OrdemServico os, SagaInstance saga) {
	}

	private static class InMemorySagaRepository implements SagaRepositoryPort {
		private final Map<UUID, SagaInstance> sagas = new HashMap<>();

		@Override
		public SagaInstance save(SagaInstance saga) {
			sagas.put(saga.getId(), saga);
			return saga;
		}

		@Override
		public Optional<SagaInstance> findById(UUID id) {
			return Optional.ofNullable(sagas.get(id));
		}

		@Override
		public Optional<SagaInstance> findByOrdemServicoId(UUID ordemServicoId) {
			return sagas.values().stream().filter(saga -> saga.getOrdemServicoId().equals(ordemServicoId)).findFirst();
		}
	}

	private static class InMemoryInbox implements InboxRepositoryPort {
		private final Map<UUID, InboxMessage> messages = new HashMap<>();

		@Override
		public InboxMessage save(InboxMessage message) {
			messages.put(message.getEventId(), message);
			return message;
		}

		@Override
		public Optional<InboxMessage> findByEventIdForUpdate(UUID eventId) {
			return Optional.ofNullable(messages.get(eventId));
		}
	}

	private static class InMemoryOrdemServicoRepository implements OrdemServicoRepositoryPort {
		private final Map<UUID, OrdemServico> ordens = new HashMap<>();

		@Override
		public OrdemServico save(OrdemServico ordemServico) {
			ordens.put(ordemServico.getId(), ordemServico);
			return ordemServico;
		}

		@Override
		public Optional<OrdemServico> findById(UUID id) {
			return Optional.ofNullable(ordens.get(id));
		}

		@Override
		public List<OrdemServico> findByClienteId(UUID clienteId) {
			return List.of();
		}

		@Override
		public List<OrdemServico> findByVeiculoId(UUID veiculoId) {
			return List.of();
		}

		@Override
		public List<OrdemServico> findByFilialId(UUID filialId) {
			return List.of();
		}

		@Override
		public List<OrdemServico> findByStatus(OrdemServicoStatus status) {
			return List.of();
		}

		@Override
		public PageResult<OrdemServico> search(OrdemServicoSearchCriteria criteria) {
			return new PageResult<>(List.of(), 0, 0, 0, 0);
		}
	}

	private static class InMemoryHistoricoRepository implements HistoricoStatusRepositoryPort {
		private final List<HistoricoStatus> history = new ArrayList<>();

		@Override
		public HistoricoStatus save(HistoricoStatus historicoStatus) {
			history.add(historicoStatus);
			return historicoStatus;
		}

		@Override
		public List<HistoricoStatus> findByOrdemServicoId(UUID ordemServicoId) {
			return history.stream().filter(h -> h.getOrdemServicoId().equals(ordemServicoId)).toList();
		}
	}

	private static class InMemoryOutboxRepository implements OutboxEventRepositoryPort {
		private final List<OutboxEvent> events = new ArrayList<>();

		@Override
		public OutboxEvent save(OutboxEvent event) {
			events.add(event);
			return event;
		}

		@Override
		public List<OutboxEvent> acquirePending(int batchSize, Instant now, Duration leaseDuration) {
			return List.of();
		}

		@Override
		public void markPublished(UUID eventId, Instant publishedAt) {
		}

		@Override
		public void markFailedAttempt(UUID eventId, Instant now, int maxAttempts, Duration initialBackoff,
				Duration maxBackoff, String error) {
		}

		@Override
		public Optional<OutboxEvent> findById(UUID eventId) {
			return events.stream().filter(event -> event.getId().equals(eventId)).findFirst();
		}
	}
}
