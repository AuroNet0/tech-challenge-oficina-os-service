package com.oficina.os.application.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.oficina.os.domain.SagaStep;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
				List.of(diagnosticItem()))));
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
	void rejectsLateDiagnosticFailureWhileWaitingApproval() {
		Fixture fixture = fixtureWaitingApproval();
		JsonNode before = businessState(fixture);

		handler.handle(event(SagaEventType.DIAGNOSTIC_FAILED, fixture, Map.of("reason", "falha tardia")));

		assertThat(fixture.os().getStatus()).isEqualTo(OrdemServicoStatus.AGUARDANDO_APROVACAO);
		assertThat(fixture.saga().getStatus()).isEqualTo(SagaStatus.WAITING_APPROVAL);
		assertThat(businessState(fixture)).isEqualTo(before);
		assertThat(inbox.messages.values()).filteredOn(message ->
				SagaEventType.DIAGNOSTIC_FAILED.equals(message.getEventType())).singleElement()
				.extracting(InboxMessage::getStatus).isEqualTo(InboxStatus.REJECTED);
	}

	@ParameterizedTest
	@ValueSource(strings = { SagaEventType.DIAGNOSTIC_FAILED, SagaEventType.QUOTE_CREATION_FAILED })
	void validFailureCompensatesOnceAndRejectsNewEventAfterCompensation(String eventType) {
		Fixture fixture = eventType.equals(SagaEventType.DIAGNOSTIC_FAILED)
				? fixture() : fixtureWaitingQuote();
		int historyCount = historicos.history.size();
		JsonNode previousOutbox = objectMapper.valueToTree(outbox.events);
		String raw = event(eventType, fixture, Map.of("reason", "falha valida"));
		UUID eventId = serializer.deserialize(raw).eventId();

		handler.handle(raw);

		assertThat(fixture.os().getStatus()).isEqualTo(OrdemServicoStatus.CANCELADA);
		assertThat(fixture.saga().getStatus()).isEqualTo(SagaStatus.COMPENSATED);
		assertThat(fixture.saga().getLastError()).isEqualTo("falha valida");
		assertThat(historicos.history).hasSize(historyCount + 1);
		assertThat(inbox.messages.get(eventId).getStatus()).isEqualTo(InboxStatus.PROCESSED);
		assertThat(objectMapper.<JsonNode>valueToTree(outbox.events)).isEqualTo(previousOutbox);
		JsonNode compensated = businessState(fixture);
		JsonNode processedInbox = objectMapper.valueToTree(inbox.messages);

		handler.handle(raw);

		assertThat(businessState(fixture)).isEqualTo(compensated);
		assertThat(objectMapper.<JsonNode>valueToTree(inbox.messages)).isEqualTo(processedInbox);

		String repeatedFailure = event(eventType, fixture, Map.of("reason", "falha valida"));
		UUID repeatedId = serializer.deserialize(repeatedFailure).eventId();
		assertThat(repeatedId).isNotEqualTo(eventId);
		handler.handle(repeatedFailure);

		assertThat(businessState(fixture)).isEqualTo(compensated);
		assertThat(inbox.messages.get(repeatedId).getStatus()).isEqualTo(InboxStatus.REJECTED);
		assertThat(inbox.messages.get(eventId).getStatus()).isEqualTo(InboxStatus.PROCESSED);
	}

	@ParameterizedTest
	@MethodSource("incompatibleFailureStates")
	void incompatibleFailurePreservesBusinessStateOnRedeliveryAndNewEventId(String eventType, SagaStatus status) {
		Fixture fixture = fixtureAtState(status);
		assertRejectedFailurePreservesBusinessState(eventType, fixture);
	}

	private static Stream<Arguments> incompatibleFailureStates() {
		return Stream.of(SagaEventType.DIAGNOSTIC_FAILED, SagaEventType.QUOTE_CREATION_FAILED)
				.flatMap(eventType -> Arrays.stream(SagaStatus.values())
						.filter(status -> status != (eventType.equals(SagaEventType.DIAGNOSTIC_FAILED)
								? SagaStatus.WAITING_DIAGNOSTIC : SagaStatus.WAITING_QUOTE))
						.map(status -> Arguments.of(eventType, status)));
	}

	@ParameterizedTest
	@CsvSource({
			"diagnostic.failed.v1, WAITING_DIAGNOSTIC, QUOTE",
			"quote.creation-failed.v1, WAITING_QUOTE, DIAGNOSTIC"
	})
	void failureRequiresMatchingStepAsWellAsStatus(String eventType, SagaStatus status, SagaStep step) {
		Fixture fixture = fixtureAtState(status);
		fixture.saga().moveTo(status, step);
		assertRejectedFailurePreservesBusinessState(eventType, fixture);
	}

	@Test
	void quoteRejectionStillCompensatesWhileWaitingApproval() {
		Fixture fixture = fixtureWaitingApproval();
		JsonNode previousOutbox = objectMapper.valueToTree(outbox.events);

		handler.handle(event(SagaEventType.QUOTE_REJECTED, fixture, Map.of("reason", "recusado")));

		assertThat(fixture.os().getStatus()).isEqualTo(OrdemServicoStatus.CANCELADA);
		assertThat(fixture.saga().getStatus()).isEqualTo(SagaStatus.COMPENSATED);
		assertThat(historicos.history).last().extracting(HistoricoStatus::getStatusNovo)
				.isEqualTo(OrdemServicoStatus.CANCELADA);
		assertThat(objectMapper.<JsonNode>valueToTree(outbox.events)).isEqualTo(previousOutbox);
	}

	private void assertRejectedFailurePreservesBusinessState(String eventType, Fixture fixture) {
		JsonNode before = businessState(fixture);
		String raw = event(eventType, fixture, Map.of("reason", "falha incompativel"));
		UUID eventId = serializer.deserialize(raw).eventId();

		handler.handle(raw);

		assertThat(businessState(fixture)).isEqualTo(before);
		assertThat(inbox.messages.get(eventId).getStatus()).isEqualTo(InboxStatus.REJECTED);
		JsonNode rejectedInbox = objectMapper.valueToTree(inbox.messages);

		handler.handle(raw);

		assertThat(businessState(fixture)).isEqualTo(before);
		assertThat(objectMapper.<JsonNode>valueToTree(inbox.messages)).isEqualTo(rejectedInbox);

		String lateFailure = event(eventType, fixture, Map.of("reason", "falha incompativel"));
		UUID lateId = serializer.deserialize(lateFailure).eventId();
		assertThat(lateId).isNotEqualTo(eventId);
		handler.handle(lateFailure);

		assertThat(businessState(fixture)).isEqualTo(before);
		assertThat(inbox.messages.get(lateId).getStatus()).isEqualTo(InboxStatus.REJECTED);
		assertThat(inbox.messages.get(eventId).getStatus()).isEqualTo(InboxStatus.REJECTED);
		assertThat(inbox.messages).hasSize(rejectedInbox.size() + 1);
	}

	private JsonNode businessState(Fixture fixture) {
		return objectMapper.valueToTree(Map.of("os", fixture.os(), "saga", fixture.saga(),
				"history", historicos.history, "outbox", outbox.events));
	}

	@Test
	void paymentFailureKeepsOrderWaitingPaymentAndAllowsLaterConfirmation() {
		Fixture fixture = fixtureAtPayment();

		handler.handle(event(SagaEventType.PAYMENT_FAILED, fixture, Map.of("reason", "cartao recusado")));

		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.AGUARDANDO_PAGAMENTO);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.WAITING_PAYMENT);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getLastError())
				.isEqualTo("cartao recusado");

		handler.handle(event(SagaEventType.PAYMENT_CONFIRMED, fixture, Map.of("paymentId", "P-1")));

		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.FINALIZADA);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.COMPLETED);
	}

	@Test
	void repairFailureWaitsForCancellationConfirmationBeforeCompensating() {
		Fixture fixture = fixtureWaitingRepair();

		handler.handle(event(SagaEventType.REPAIR_FAILED, fixture, Map.of("reason", "peca indisponivel")));

		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.COMPENSATING);
		assertThat(outbox.events).extracting(OutboxEvent::getEventType).contains(SagaEventType.REPAIR_CANCEL_COMMAND);

		handler.handle(event(SagaEventType.REPAIR_CANCELLED, fixture, Map.of()));

		assertThat(ordens.findById(fixture.os().getId()).orElseThrow().getStatus())
				.isEqualTo(OrdemServicoStatus.CANCELADA);
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.COMPENSATED);
	}

	@Test
	void repairCancellationFailureEndsAsFailed() {
		Fixture fixture = fixtureWaitingRepair();

		handler.handle(event(SagaEventType.REPAIR_FAILED, fixture, Map.of("reason", "peca indisponivel")));
		handler.handle(event(SagaEventType.REPAIR_CANCELLATION_FAILED, fixture, Map.of("reason", "ja iniciado")));

		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.FAILED);
	}

	@Test
	void rejectsDiagnosticCompletedWithoutBillingFields() {
		Fixture fixture = fixture();
		handler.handle(event(SagaEventType.DIAGNOSTIC_STARTED, fixture, Map.of()));

		handler.handle(event(SagaEventType.DIAGNOSTIC_COMPLETED, fixture, Map.of("items",
				List.of(Map.of("itemId", "1", "descricao", "Troca de oleo", "precoPrevisto", "120.00")))));

		assertThat(inbox.messages.values()).anySatisfy(message -> assertThat(message.getStatus())
				.isEqualTo(InboxStatus.REJECTED));
		assertThat(sagas.findById(fixture.saga().getId()).orElseThrow().getStatus())
				.isEqualTo(SagaStatus.WAITING_DIAGNOSTIC);
	}

	private Fixture fixtureAtState(SagaStatus status) {
		return switch (status) {
			case WAITING_DIAGNOSTIC -> fixture();
			case WAITING_QUOTE -> fixtureWaitingQuote();
			case WAITING_APPROVAL -> fixtureWaitingApproval();
			case WAITING_REPAIR -> fixtureWaitingRepair();
			case WAITING_PAYMENT -> fixtureAtPayment();
			case COMPLETED -> {
				Fixture fixture = fixtureAtPayment();
				handler.handle(event(SagaEventType.PAYMENT_CONFIRMED, fixture, Map.of("paymentId", "P-1")));
				yield fixture;
			}
			case COMPENSATING, FAILED -> {
				Fixture fixture = fixtureWaitingRepair();
				handler.handle(event(SagaEventType.REPAIR_FAILED, fixture, Map.of("reason", "falha")));
				if (status == SagaStatus.FAILED) {
					handler.handle(event(SagaEventType.REPAIR_CANCELLATION_FAILED, fixture,
							Map.of("reason", "ja iniciado")));
				}
				yield fixture;
			}
			case COMPENSATED -> {
				Fixture fixture = fixtureWaitingApproval();
				handler.handle(event(SagaEventType.QUOTE_REJECTED, fixture, Map.of("reason", "recusado")));
				yield fixture;
			}
			case STARTED -> {
				Fixture fixture = fixture();
				fixture.saga().moveTo(status, SagaStep.DIAGNOSTIC);
				yield fixture;
			}
		};
	}

	private Fixture fixtureWaitingQuote() {
		Fixture fixture = fixture();
		handler.handle(event(SagaEventType.DIAGNOSTIC_STARTED, fixture, Map.of()));
		handler.handle(event(SagaEventType.DIAGNOSTIC_COMPLETED, fixture,
				Map.of("items", List.of(diagnosticItem()))));
		return fixture;
	}

	private Fixture fixtureWaitingApproval() {
		Fixture fixture = fixtureWaitingQuote();
		handler.handle(event(SagaEventType.QUOTE_CREATED, fixture, Map.of("quoteId", "Q-1")));
		return fixture;
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

	private Fixture fixtureWaitingRepair() {
		Fixture fixture = fixture();
		UUID correlationId = fixture.saga().getCorrelationId();
		fixture.os().iniciarDiagnostico("diag", correlationId);
		fixture.os().aguardarAprovacao("quote", correlationId);
		fixture.saga().moveTo(SagaStatus.WAITING_REPAIR, com.oficina.os.domain.SagaStep.REPAIR);
		ordens.save(fixture.os());
		sagas.save(fixture.saga());
		return fixture;
	}

	private Map<String, String> diagnosticItem() {
		return Map.of(
				"itemId", "1",
				"descricao", "Troca de oleo",
				"tipo", "SERVICO",
				"quantidade", "1",
				"unidadePreco", "UNIDADE",
				"precoPrevisto", "120.00");
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
