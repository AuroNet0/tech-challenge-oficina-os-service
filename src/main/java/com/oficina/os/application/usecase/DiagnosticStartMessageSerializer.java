package com.oficina.os.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.OrdemServico;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DiagnosticStartMessageSerializer {

	private final ObjectMapper objectMapper;

	public DiagnosticStartMessageSerializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String serialize(UUID eventId, OrdemServico ordemServico, UUID correlationId) {
		try {
			return objectMapper.writeValueAsString(
					OutboxEvent.diagnosticStartEnvelope(eventId, ordemServico, correlationId));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("could not serialize diagnostic start envelope", ex);
		}
	}
}
