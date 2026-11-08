package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.InboxStatus;
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
@Table(name = "inbox_messages")
class InboxMessageJpaEntity {

	@Id
	@Column(name = "event_id")
	private UUID eventId;

	@Column(name = "saga_id")
	private UUID sagaId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InboxStatus status;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	protected InboxMessageJpaEntity() {
	}

	InboxMessageJpaEntity(UUID eventId, UUID sagaId, String eventType, String payload, InboxStatus status,
			Instant receivedAt, Instant processedAt, String lastError) {
		this.eventId = eventId;
		this.sagaId = sagaId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = status;
		this.receivedAt = receivedAt;
		this.processedAt = processedAt;
		this.lastError = lastError;
	}

	UUID getEventId() {
		return eventId;
	}

	UUID getSagaId() {
		return sagaId;
	}

	String getEventType() {
		return eventType;
	}

	String getPayload() {
		return payload;
	}

	InboxStatus getStatus() {
		return status;
	}

	Instant getReceivedAt() {
		return receivedAt;
	}

	Instant getProcessedAt() {
		return processedAt;
	}

	String getLastError() {
		return lastError;
	}
}
