package com.oficina.os.domain;

import java.time.Instant;
import java.util.UUID;

public record DiagnosticStartEnvelope(UUID eventId, String eventType, Instant occurredAt, UUID correlationId,
		UUID sagaId, UUID osId, String source, DiagnosticStartPayload payload) {
}
