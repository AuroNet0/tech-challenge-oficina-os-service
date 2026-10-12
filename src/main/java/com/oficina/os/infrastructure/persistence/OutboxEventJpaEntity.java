package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEventJpaEntity {

	@Id
	private UUID id;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "aggregate_type", nullable = false)
	private String aggregateType;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OutboxStatus status;

	@Column(name = "correlation_id")
	private UUID correlationId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at")
	private Instant nextAttemptAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	protected OutboxEventJpaEntity() {
	}

	OutboxEventJpaEntity(UUID id, UUID aggregateId, String aggregateType, String eventType, String payload,
			OutboxStatus status, UUID correlationId, Instant createdAt, Instant publishedAt, int attempts,
			Instant nextAttemptAt, String lastError, Instant lockedUntil) {
		this.id = id;
		this.aggregateId = aggregateId;
		this.aggregateType = aggregateType;
		this.eventType = eventType;
		this.payload = payload;
		this.status = status;
		this.correlationId = correlationId;
		this.createdAt = createdAt;
		this.publishedAt = publishedAt;
		this.attempts = attempts;
		this.nextAttemptAt = nextAttemptAt;
		this.lastError = lastError;
		this.lockedUntil = lockedUntil;
	}

	UUID getId() {
		return id;
	}

	UUID getAggregateId() {
		return aggregateId;
	}

	String getAggregateType() {
		return aggregateType;
	}

	String getEventType() {
		return eventType;
	}

	String getPayload() {
		return payload;
	}

	OutboxStatus getStatus() {
		return status;
	}

	UUID getCorrelationId() {
		return correlationId;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getPublishedAt() {
		return publishedAt;
	}

	int getAttempts() {
		return attempts;
	}

	Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	String getLastError() {
		return lastError;
	}

	Instant getLockedUntil() {
		return lockedUntil;
	}
}
