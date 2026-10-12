package com.oficina.os.application.saga;

import com.oficina.os.application.exception.ResourceNotFoundException;
import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.application.port.out.InboxRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.application.port.out.SagaRepositoryPort;
import com.oficina.os.domain.DomainException;
import com.oficina.os.domain.InboxMessage;
import com.oficina.os.domain.InboxStatus;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OrdemServicoStatus;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.OutboxStatus;
import com.oficina.os.domain.SagaInstance;
import com.oficina.os.domain.SagaStatus;
import com.oficina.os.domain.SagaStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SagaEventHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(SagaEventHandler.class);

	private final InboxRepositoryPort inbox;
	private final SagaRepositoryPort sagas;
	private final OrdemServicoRepositoryPort ordensServico;
	private final HistoricoStatusRepositoryPort historicos;
	private final OutboxEventRepositoryPort outboxEvents;
	private final SagaMessageSerializer serializer;
	private final SagaStateMachine stateMachine;

	public SagaEventHandler(InboxRepositoryPort inbox, SagaRepositoryPort sagas,
			OrdemServicoRepositoryPort ordensServico, HistoricoStatusRepositoryPort historicos,
			OutboxEventRepositoryPort outboxEvents, SagaMessageSerializer serializer, SagaStateMachine stateMachine) {
		this.inbox = inbox;
		this.sagas = sagas;
		this.ordensServico = ordensServico;
		this.historicos = historicos;
		this.outboxEvents = outboxEvents;
		this.serializer = serializer;
		this.stateMachine = stateMachine;
	}

	@Transactional
	public void handle(String rawPayload) {
		SagaEnvelope envelope = serializer.deserialize(rawPayload);
		validateEnvelopeFields(envelope);
		InboxMessage message = inbox.findByEventIdForUpdate(envelope.eventId())
				.orElseGet(() -> inbox.save(InboxMessage.received(envelope.eventId(), envelope.sagaId(),
						envelope.eventType(), rawPayload)));
		if (message.getStatus() == InboxStatus.PROCESSED || message.getStatus() == InboxStatus.REJECTED) {
			LOGGER.info("saga duplicate event ignored eventId={} sagaId={} osId={} correlationId={}",
					envelope.eventId(), envelope.sagaId(), envelope.osId(), envelope.correlationId());
			return;
		}
		message.markProcessing();
		inbox.save(message);

		try {
			SagaInstance saga = sagas.findById(envelope.sagaId())
					.orElseThrow(() -> new DomainException("saga not found"));
			stateMachine.validateEnvelope(saga, envelope);
			OrdemServico ordemServico = ordensServico.findById(envelope.osId())
					.orElseThrow(() -> new ResourceNotFoundException("ordemServico not found"));
			SagaStatus previousStatus = saga.getStatus();
			dispatch(envelope, saga, ordemServico);
			sagas.save(saga);
			message.markProcessed();
			inbox.save(message);
			LOGGER.info("saga event processed eventId={} sagaId={} osId={} correlationId={} previousState={} newState={}",
					envelope.eventId(), saga.getId(), saga.getOrdemServicoId(), envelope.correlationId(),
					previousStatus, saga.getStatus());
		}
		catch (DomainException | IllegalArgumentException | ResourceNotFoundException ex) {
			message.markRejected(ex.getMessage());
			inbox.save(message);
			LOGGER.warn("saga event rejected eventId={} sagaId={} osId={} correlationId={} result={}",
					envelope.eventId(), envelope.sagaId(), envelope.osId(), envelope.correlationId(), ex.getMessage());
		}
	}

	private void dispatch(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		switch (envelope.eventType()) {
			case SagaEventType.DIAGNOSTIC_STARTED -> diagnosticStarted(envelope, saga, ordemServico);
			case SagaEventType.DIAGNOSTIC_COMPLETED -> diagnosticCompleted(envelope, saga, ordemServico);
			case SagaEventType.DIAGNOSTIC_FAILED -> failBeforePhysicalWork(envelope, saga, ordemServico);
			case SagaEventType.QUOTE_CREATED -> quoteCreated(envelope, saga, ordemServico);
			case SagaEventType.QUOTE_CREATION_FAILED -> failBeforePhysicalWork(envelope, saga, ordemServico);
			case SagaEventType.QUOTE_APPROVED -> quoteApproved(envelope, saga);
			case SagaEventType.QUOTE_REJECTED -> quoteRejected(envelope, saga, ordemServico);
			case SagaEventType.REPAIR_STARTED -> repairStarted(envelope, saga, ordemServico);
			case SagaEventType.REPAIR_COMPLETED -> repairCompleted(envelope, saga, ordemServico);
			case SagaEventType.REPAIR_FAILED -> repairFailed(envelope, saga);
			case SagaEventType.PAYMENT_CONFIRMED -> paymentConfirmed(envelope, saga, ordemServico);
			case SagaEventType.PAYMENT_FAILED -> paymentFailed(envelope, saga, ordemServico);
			default -> throw new DomainException("unsupported event type " + envelope.eventType());
		}
	}

	private void diagnosticStarted(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requireDiagnostic(saga, envelope.eventType());
		if (ordemServico.getStatus() == OrdemServicoStatus.RECEBIDA) {
			historicos.save(ordemServico.iniciarDiagnostico("Diagnostico iniciado", envelope.correlationId()));
			ordensServico.save(ordemServico);
		}
		else if (ordemServico.getStatus() != OrdemServicoStatus.EM_DIAGNOSTICO) {
			throw new DomainException("ordemServico is not ready for diagnostic start");
		}
	}

	private void diagnosticCompleted(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requireDiagnostic(saga, envelope.eventType());
		requireOsStatus(ordemServico, OrdemServicoStatus.EM_DIAGNOSTICO);
		DiagnosticCompletedPayload payload = serializer.convertPayload(envelope, DiagnosticCompletedPayload.class);
		if (payload.items() == null || payload.items().isEmpty()) {
			throw new DomainException("diagnostic items are required");
		}
		createCommand(SagaEventType.QUOTE_CREATE_COMMAND, envelope, Map.of("items", payload.items()));
		saga.moveTo(SagaStatus.WAITING_QUOTE, SagaStep.QUOTE);
	}

	private void quoteCreated(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requireQuote(saga, envelope.eventType());
		QuoteCreatedPayload payload = serializer.convertPayload(envelope, QuoteCreatedPayload.class);
		requireText(payload.quoteId(), "quoteId is required");
		requireOsStatus(ordemServico, OrdemServicoStatus.EM_DIAGNOSTICO);
		historicos.save(ordemServico.aguardarAprovacao("Orcamento criado " + payload.quoteId(),
				envelope.correlationId()));
		ordensServico.save(ordemServico);
		saga.moveTo(SagaStatus.WAITING_APPROVAL, SagaStep.APPROVAL);
	}

	private void quoteApproved(SagaEnvelope envelope, SagaInstance saga) {
		stateMachine.requireApproval(saga, envelope.eventType());
		createCommand(SagaEventType.REPAIR_START_COMMAND, envelope, Map.of());
		saga.moveTo(SagaStatus.WAITING_REPAIR, SagaStep.REPAIR);
	}

	private void quoteRejected(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requireApproval(saga, envelope.eventType());
		historicos.save(ordemServico.cancelar("Orcamento recusado", envelope.correlationId()));
		ordensServico.save(ordemServico);
		saga.compensate("quote rejected");
	}

	private void repairStarted(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requireRepair(saga, envelope.eventType());
		requireOsStatus(ordemServico, OrdemServicoStatus.AGUARDANDO_APROVACAO);
		historicos.save(ordemServico.iniciarExecucao("Reparo iniciado", envelope.correlationId()));
		ordensServico.save(ordemServico);
	}

	private void repairCompleted(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requireRepair(saga, envelope.eventType());
		requireOsStatus(ordemServico, OrdemServicoStatus.EM_EXECUCAO);
		RepairCompletedPayload payload = serializer.convertPayload(envelope, RepairCompletedPayload.class);
		requireText(payload.repairId(), "repairId is required");
		historicos.save(ordemServico.aguardarPagamento("Reparo concluido " + payload.repairId(),
				envelope.correlationId()));
		ordensServico.save(ordemServico);
		createCommand(SagaEventType.PAYMENT_REQUEST_COMMAND, envelope, Map.of("repairId", payload.repairId()));
		saga.moveTo(SagaStatus.WAITING_PAYMENT, SagaStep.PAYMENT);
	}

	private void paymentConfirmed(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requirePayment(saga, envelope.eventType());
		PaymentConfirmedPayload payload = serializer.convertPayload(envelope, PaymentConfirmedPayload.class);
		requireText(payload.paymentId(), "paymentId is required");
		requireOsStatus(ordemServico, OrdemServicoStatus.AGUARDANDO_PAGAMENTO);
		historicos.save(ordemServico.finalizar("Pagamento confirmado " + payload.paymentId(),
				envelope.correlationId()));
		ordensServico.save(ordemServico);
		saga.complete();
	}

	private void failBeforePhysicalWork(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		if (saga.getStatus() == SagaStatus.WAITING_DIAGNOSTIC) {
			stateMachine.requireDiagnostic(saga, envelope.eventType());
		}
		else if (saga.getStatus() == SagaStatus.WAITING_QUOTE) {
			stateMachine.requireQuote(saga, envelope.eventType());
		}
		String reason = failureReason(envelope);
		historicos.save(ordemServico.cancelar("Saga compensada antes de execucao fisica: " + reason,
				envelope.correlationId()));
		ordensServico.save(ordemServico);
		saga.compensate(reason);
	}

	private void repairFailed(SagaEnvelope envelope, SagaInstance saga) {
		stateMachine.requireRepair(saga, envelope.eventType());
		createCommand(SagaEventType.REPAIR_CANCEL_COMMAND, envelope, Map.of("reason", failureReason(envelope)));
		saga.moveTo(SagaStatus.COMPENSATING, SagaStep.COMPENSATION);
		saga.fail(failureReason(envelope));
	}

	private void paymentFailed(SagaEnvelope envelope, SagaInstance saga, OrdemServico ordemServico) {
		stateMachine.requirePayment(saga, envelope.eventType());
		requireOsStatus(ordemServico, OrdemServicoStatus.AGUARDANDO_PAGAMENTO);
		saga.fail(failureReason(envelope));
	}

	private void createCommand(String eventType, SagaEnvelope envelope, Object payload) {
		UUID commandId = UUID.randomUUID();
		String body = serializer.serializeCommand(commandId, eventType, envelope.correlationId(), envelope.sagaId(),
				envelope.osId(), payload);
		outboxEvents.save(OutboxEvent.restore(commandId, envelope.osId(), OutboxEvent.ORDEM_SERVICO, eventType, body,
				OutboxStatus.PENDING, envelope.correlationId(), Instant.now(), null, 0, null, null, null));
	}

	private String failureReason(SagaEnvelope envelope) {
		FailurePayload payload = serializer.convertPayload(envelope, FailurePayload.class);
		return payload.reason() == null || payload.reason().isBlank() ? envelope.eventType() : payload.reason();
	}

	private void requireOsStatus(OrdemServico ordemServico, OrdemServicoStatus expected) {
		if (ordemServico.getStatus() != expected) {
			throw new DomainException("ordemServico status must be " + expected);
		}
	}

	private void validateEnvelopeFields(SagaEnvelope envelope) {
		if (envelope.eventId() == null || envelope.eventType() == null || envelope.eventType().isBlank()
				|| envelope.correlationId() == null || envelope.sagaId() == null || envelope.osId() == null
				|| envelope.payload() == null) {
			throw new IllegalArgumentException("event envelope is missing required fields");
		}
	}

	private void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new DomainException(message);
		}
	}
}
