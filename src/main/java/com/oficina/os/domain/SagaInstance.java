package com.oficina.os.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

public class SagaInstance {

	private static final EnumSet<SagaStatus> TERMINAL_STATUSES = EnumSet.of(
			SagaStatus.COMPLETED, SagaStatus.COMPENSATED, SagaStatus.FAILED);

	private final UUID id;
	private final UUID ordemServicoId;
	private SagaStatus status;
	private SagaStep currentStep;
	private final Instant createdAt;
	private Instant updatedAt;
	private Instant completedAt;
	private final UUID correlationId;
	private Long version;
	private String lastError;

	private SagaInstance(UUID id, UUID ordemServicoId, SagaStatus status, SagaStep currentStep, Instant createdAt,
			Instant updatedAt, Instant completedAt, UUID correlationId, Long version, String lastError) {
		this.id = Objects.requireNonNull(id, "id is required");
		this.ordemServicoId = Objects.requireNonNull(ordemServicoId, "ordemServicoId is required");
		this.status = Objects.requireNonNull(status, "status is required");
		this.currentStep = Objects.requireNonNull(currentStep, "currentStep is required");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
		this.completedAt = completedAt;
		this.correlationId = Objects.requireNonNull(correlationId, "correlationId is required");
		this.version = version;
		this.lastError = lastError;
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt cannot be before createdAt");
		}
	}

	public static SagaInstance start(UUID ordemServicoId, UUID correlationId) {
		Instant now = Instant.now();
		return new SagaInstance(UUID.randomUUID(), ordemServicoId, SagaStatus.WAITING_DIAGNOSTIC,
				SagaStep.DIAGNOSTIC, now, now, null, correlationId, null, null);
	}

	public static SagaInstance restore(UUID id, UUID ordemServicoId, SagaStatus status, SagaStep currentStep,
			Instant createdAt, Instant updatedAt, Instant completedAt, UUID correlationId, Long version,
			String lastError) {
		return new SagaInstance(id, ordemServicoId, status, currentStep, createdAt, updatedAt, completedAt,
				correlationId, version, lastError);
	}

	public void require(SagaStatus expectedStatus, SagaStep expectedStep, String eventType) {
		if (isTerminal()) {
			throw new DomainException("terminal saga cannot process event " + eventType);
		}
		if (status != expectedStatus || currentStep != expectedStep) {
			throw new DomainException("event " + eventType + " is incompatible with saga state " + status + "/"
					+ currentStep);
		}
	}

	public void moveTo(SagaStatus newStatus, SagaStep newStep) {
		change(newStatus, newStep, null, false);
	}

	public void recordRecoverableError(String error) {
		if (isTerminal()) {
			throw new DomainException("terminal saga cannot be changed");
		}
		this.updatedAt = Instant.now();
		this.lastError = error;
	}

	public void fail(String error) {
		change(SagaStatus.FAILED, currentStep, error, true);
	}

	public void compensate(String error) {
		change(SagaStatus.COMPENSATED, SagaStep.COMPENSATION, error, true);
	}

	public void complete() {
		change(SagaStatus.COMPLETED, SagaStep.DONE, null, true);
	}

	private void change(SagaStatus newStatus, SagaStep newStep, String error, boolean terminal) {
		if (isTerminal()) {
			throw new DomainException("terminal saga cannot be changed");
		}
		this.status = Objects.requireNonNull(newStatus, "newStatus is required");
		this.currentStep = Objects.requireNonNull(newStep, "newStep is required");
		this.updatedAt = Instant.now();
		this.lastError = error;
		if (terminal) {
			this.completedAt = updatedAt;
		}
	}

	public boolean isTerminal() {
		return TERMINAL_STATUSES.contains(status);
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrdemServicoId() {
		return ordemServicoId;
	}

	public SagaStatus getStatus() {
		return status;
	}

	public SagaStep getCurrentStep() {
		return currentStep;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public UUID getCorrelationId() {
		return correlationId;
	}

	public Long getVersion() {
		return version;
	}

	public String getLastError() {
		return lastError;
	}
}
