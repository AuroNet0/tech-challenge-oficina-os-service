package com.oficina.os.application.saga;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record SagaEnvelope(UUID eventId, String eventType, Instant occurredAt, UUID correlationId, UUID sagaId,
		UUID osId, String source, JsonNode payload) {
}
