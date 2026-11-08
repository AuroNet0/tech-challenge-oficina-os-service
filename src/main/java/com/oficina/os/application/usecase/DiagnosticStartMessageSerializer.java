package com.oficina.os.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.OrdemServico;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DiagnosticStartMessageSerializer {

	private final ObjectWriter messageWriter;

	public DiagnosticStartMessageSerializer(ObjectMapper objectMapper) {
		this.messageWriter = objectMapper.writer().without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	public String serialize(UUID eventId, OrdemServico ordemServico, UUID correlationId) {
		return serialize(eventId, ordemServico, correlationId, null);
	}

	public String serialize(UUID eventId, OrdemServico ordemServico, UUID correlationId, UUID sagaId) {
		try {
			return messageWriter.writeValueAsString(
					OutboxEvent.diagnosticStartEnvelope(eventId, ordemServico, correlationId, sagaId));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("could not serialize diagnostic start envelope", ex);
		}
	}
}
