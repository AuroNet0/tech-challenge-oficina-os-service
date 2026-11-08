package com.oficina.os.application.saga;

import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.application.port.out.SagaRepositoryPort;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.SagaInstance;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrdemServicoSagaOrchestrator {

	private final SagaRepositoryPort sagas;
	private final OutboxEventRepositoryPort outboxEvents;
	private final SagaMessageSerializer serializer;

	public OrdemServicoSagaOrchestrator(SagaRepositoryPort sagas, OutboxEventRepositoryPort outboxEvents,
			SagaMessageSerializer serializer) {
		this.sagas = sagas;
		this.outboxEvents = outboxEvents;
		this.serializer = serializer;
	}

	public SagaInstance start(UUID ordemServicoId, UUID correlationId, Object diagnosticPayload) {
		SagaInstance saga = sagas.save(SagaInstance.start(ordemServicoId, correlationId));
		UUID eventId = UUID.randomUUID();
		String payload = serializer.serializeCommand(eventId, SagaEventType.DIAGNOSTIC_START_COMMAND, correlationId,
				saga.getId(), ordemServicoId, diagnosticPayload);
		outboxEvents.save(OutboxEvent.restore(eventId, ordemServicoId, OutboxEvent.ORDEM_SERVICO,
				SagaEventType.DIAGNOSTIC_START_COMMAND, payload, com.oficina.os.domain.OutboxStatus.PENDING,
				correlationId, java.time.Instant.now(), null, 0, null, null, null));
		return saga;
	}
}
