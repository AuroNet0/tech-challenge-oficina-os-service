package com.oficina.os.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class OutboxEvent {

	public static final String DIAGNOSTIC_START_V1 = "diagnostic.start.v1";
	public static final String ORDEM_SERVICO = "OrdemServico";
	public static final String SOURCE = "os-service";

	private final UUID id;
	private final UUID aggregateId;
	private final String aggregateType;
	private final String eventType;
	private final String payload;
	private OutboxStatus status;
	private final UUID correlationId;
	private final Instant createdAt;
	private Instant publishedAt;
	private int attempts;
	private Instant nextAttemptAt;
	private String lastError;
	private Instant lockedUntil;

	private OutboxEvent(UUID id, UUID aggregateId, String aggregateType, String eventType, String payload,
			OutboxStatus status, UUID correlationId, Instant createdAt, Instant publishedAt, int attempts,
			Instant nextAttemptAt, String lastError, Instant lockedUntil) {
		this.id = Objects.requireNonNull(id, "id is required");
		this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
		this.aggregateType = requireText(aggregateType, "aggregateType is required");
		this.eventType = requireText(eventType, "eventType is required");
		this.payload = requireText(payload, "payload is required");
		this.status = Objects.requireNonNull(status, "status is required");
		this.correlationId = correlationId;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.publishedAt = publishedAt;
		if (attempts < 0) {
			throw new IllegalArgumentException("attempts cannot be negative");
		}
		this.attempts = attempts;
		this.nextAttemptAt = nextAttemptAt;
		this.lastError = lastError;
		this.lockedUntil = lockedUntil;
	}

	public static OutboxEvent pending(UUID aggregateId, String aggregateType, String eventType, String payload,
			UUID correlationId) {
		return restore(UUID.randomUUID(), aggregateId, aggregateType, eventType, payload, OutboxStatus.PENDING,
				correlationId, Instant.now(), null, 0, null, null, null);
	}

	public static DiagnosticStartEnvelope diagnosticStartEnvelope(UUID eventId, OrdemServico ordemServico,
			UUID correlationId) {
		return new DiagnosticStartEnvelope(eventId, DIAGNOSTIC_START_V1, ordemServico.getCreatedAt(), correlationId,
				null, ordemServico.getId(), SOURCE, new DiagnosticStartPayload(ordemServico.getClienteId(),
						ordemServico.getVeiculoId(), ordemServico.getFilialId(), ordemServico.getObservacoes()));
	}

	public static OutboxEvent restore(UUID id, UUID aggregateId, String aggregateType, String eventType, String payload,
			OutboxStatus status, UUID correlationId, Instant createdAt, Instant publishedAt, int attempts,
			Instant nextAttemptAt, String lastError, Instant lockedUntil) {
		return new OutboxEvent(id, aggregateId, aggregateType, eventType, payload, status, correlationId, createdAt,
				publishedAt, attempts, nextAttemptAt, lastError, lockedUntil);
	}

	public void markPublished(Instant publishedAt) {
		if (status == OutboxStatus.FAILED) {
			throw new DomainException("failed outbox event cannot be published automatically");
		}
		this.status = OutboxStatus.PUBLISHED;
		this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt is required");
		this.lockedUntil = null;
		this.nextAttemptAt = null;
		this.lastError = null;
	}

	public void markFailedAttempt(Instant nextAttemptAt, String error, int maxAttempts) {
		this.attempts++;
		this.lastError = requireText(error, "error is required");
		this.lockedUntil = null;
		if (this.attempts >= maxAttempts) {
			this.status = OutboxStatus.FAILED;
			this.nextAttemptAt = null;
			return;
		}
		this.status = OutboxStatus.PENDING;
		this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt is required");
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	public UUID getId() {
		return id;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public OutboxStatus getStatus() {
		return status;
	}

	public UUID getCorrelationId() {
		return correlationId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}
}
