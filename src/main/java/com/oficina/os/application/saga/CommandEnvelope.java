package com.oficina.os.application.saga;

import java.time.Instant;
import java.util.UUID;

public record CommandEnvelope<T>(UUID eventId, String eventType, Instant occurredAt, UUID correlationId, UUID sagaId,
		UUID osId, String source, T payload) {
}
