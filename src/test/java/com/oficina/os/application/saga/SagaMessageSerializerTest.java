package com.oficina.os.application.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SagaMessageSerializerTest {

	@ParameterizedTest
	@ValueSource(strings = {"diagnostic.start.v1", "quote.create.v1", "repair.start.v1",
			"repair.cancel.v1", "payment.request.v1"})
	void serializesEveryCommandWithIsoInstantAndPreservesEnvelope(String eventType) throws Exception {
		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
		SagaMessageSerializer serializer = new SagaMessageSerializer(mapper);
		UUID eventId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		UUID sagaId = UUID.randomUUID();
		UUID osId = UUID.randomUUID();
		Object payload = switch (eventType) {
			case "diagnostic.start.v1" -> Map.of("clienteId", UUID.randomUUID(),
					"veiculoId", UUID.randomUUID(), "filialId", UUID.randomUUID());
			case "quote.create.v1" -> Map.of("items", List.of(Map.of(
					"itemId", "item-001", "descricao", "Troca de pastilhas", "tipo", "PECA",
					"quantidade", "2.500", "unidadePreco", "UNIDADE", "precoPrevisto", "150.000100")));
			case "repair.cancel.v1" -> Map.of("reason", "Falha tecnica");
			case "payment.request.v1" -> Map.of("repairId", UUID.randomUUID().toString());
			default -> Map.of();
		};

		Instant before = Instant.now();
		JsonNode json = mapper.readTree(serializer.serializeCommand(eventId, eventType,
				correlationId, sagaId, osId, payload));
		Instant after = Instant.now();

		assertThat(json.get("occurredAt").isTextual()).isTrue();
		assertThat(Instant.parse(json.get("occurredAt").asText())).isBetween(before, after);
		assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
		assertThat(json.get("eventType").asText()).isEqualTo(eventType);
		assertThat(json.get("correlationId").asText()).isEqualTo(correlationId.toString());
		assertThat(json.get("sagaId").asText()).isEqualTo(sagaId.toString());
		assertThat(json.get("osId").asText()).isEqualTo(osId.toString());
		assertThat(json.get("source").asText()).isEqualTo("os-service");
		assertThat(json.get("payload")).isEqualTo(mapper.valueToTree(payload));
		assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isTrue();
	}
}
