package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.SagaStatus;
import com.oficina.os.domain.SagaStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
class SagaJpaEntity {

	@Id
	private UUID id;

	@Column(name = "ordem_servico_id", nullable = false, unique = true)
	private UUID ordemServicoId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SagaStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "current_step", nullable = false)
	private SagaStep currentStep;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "correlation_id", nullable = false)
	private UUID correlationId;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	protected SagaJpaEntity() {
	}

	SagaJpaEntity(UUID id, UUID ordemServicoId, SagaStatus status, SagaStep currentStep, Instant createdAt,
			Instant updatedAt, Instant completedAt, UUID correlationId, Long version, String lastError) {
		this.id = id;
		this.ordemServicoId = ordemServicoId;
		this.status = status;
		this.currentStep = currentStep;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.completedAt = completedAt;
		this.correlationId = correlationId;
		this.version = version;
		this.lastError = lastError;
	}

	UUID getId() {
		return id;
	}

	UUID getOrdemServicoId() {
		return ordemServicoId;
	}

	SagaStatus getStatus() {
		return status;
	}

	SagaStep getCurrentStep() {
		return currentStep;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	Instant getCompletedAt() {
		return completedAt;
	}

	UUID getCorrelationId() {
		return correlationId;
	}

	Long getVersion() {
		return version;
	}

	String getLastError() {
		return lastError;
	}
}
