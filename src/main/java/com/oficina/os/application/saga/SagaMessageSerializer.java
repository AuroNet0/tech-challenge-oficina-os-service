package com.oficina.os.application.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.oficina.os.domain.OutboxEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class SagaMessageSerializer {

	private final ObjectMapper objectMapper;
	private final ObjectWriter messageWriter;

	public SagaMessageSerializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.messageWriter = objectMapper.writer().without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	public String serializeCommand(UUID eventId, String eventType, UUID correlationId, UUID sagaId, UUID osId,
			Object payload) {
		try {
			return messageWriter.writeValueAsString(new CommandEnvelope<>(eventId, eventType, Instant.now(),
					correlationId, sagaId, osId, OutboxEvent.SOURCE, payload));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("could not serialize saga command " + eventType, ex);
		}
	}

	public SagaEnvelope deserialize(String payload) {
		try {
			return objectMapper.readValue(payload, SagaEnvelope.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("invalid saga envelope", ex);
		}
	}

	public <T> T convertPayload(SagaEnvelope envelope, Class<T> type) {
		return objectMapper.convertValue(envelope.payload(), type);
	}
}
