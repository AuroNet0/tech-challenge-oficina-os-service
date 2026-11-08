package com.oficina.os.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class InboxMessage {

	private final UUID eventId;
	private final UUID sagaId;
	private final String eventType;
	private final String payload;
	private InboxStatus status;
	private final Instant receivedAt;
	private Instant processedAt;
	private String lastError;

	private InboxMessage(UUID eventId, UUID sagaId, String eventType, String payload, InboxStatus status,
			Instant receivedAt, Instant processedAt, String lastError) {
		this.eventId = Objects.requireNonNull(eventId, "eventId is required");
		this.sagaId = sagaId;
		this.eventType = requireText(eventType, "eventType is required");
		this.payload = requireText(payload, "payload is required");
		this.status = Objects.requireNonNull(status, "status is required");
		this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt is required");
		this.processedAt = processedAt;
		this.lastError = lastError;
	}

	public static InboxMessage received(UUID eventId, UUID sagaId, String eventType, String payload) {
		return new InboxMessage(eventId, sagaId, eventType, payload, InboxStatus.RECEIVED, Instant.now(), null, null);
	}

	public static InboxMessage restore(UUID eventId, UUID sagaId, String eventType, String payload,
			InboxStatus status, Instant receivedAt, Instant processedAt, String lastError) {
		return new InboxMessage(eventId, sagaId, eventType, payload, status, receivedAt, processedAt, lastError);
	}

	public void markProcessing() {
		if (status == InboxStatus.PROCESSED || status == InboxStatus.REJECTED) {
			throw new DomainException("inbox message already finished");
		}
		status = InboxStatus.PROCESSING;
	}

	public void markProcessed() {
		status = InboxStatus.PROCESSED;
		processedAt = Instant.now();
		lastError = null;
	}

	public void markRejected(String error) {
		status = InboxStatus.REJECTED;
		processedAt = Instant.now();
		lastError = trim(requireText(error, "error is required"));
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String trim(String value) {
		return value.length() <= 4000 ? value : value.substring(0, 4000);
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getSagaId() {
		return sagaId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public InboxStatus getStatus() {
		return status;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public String getLastError() {
		return lastError;
	}
}
