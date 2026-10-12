package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.SagaInstance;
import com.oficina.os.domain.SagaStatus;
import com.oficina.os.domain.SagaStep;

import java.time.Instant;
import java.util.UUID;

public record SagaResponse(UUID sagaId, UUID ordemServicoId, SagaStatus status, SagaStep currentStep,
		Instant createdAt, Instant updatedAt, Instant completedAt, String lastError) {

	public static SagaResponse from(SagaInstance saga) {
		return new SagaResponse(saga.getId(), saga.getOrdemServicoId(), saga.getStatus(), saga.getCurrentStep(),
				saga.getCreatedAt(), saga.getUpdatedAt(), saga.getCompletedAt(), saga.getLastError());
	}
}
