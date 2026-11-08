package com.oficina.os.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OutboxEvent;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticStartMessageSerializerTest {

	@Test
	void serializesVersionedDiagnosticStartEnvelope() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		DiagnosticStartMessageSerializer serializer = new DiagnosticStartMessageSerializer(objectMapper);
		Cliente cliente = Cliente.novo("Maria", "52998224725", null, null);
		Veiculo veiculo = Veiculo.novo(cliente.getId(), "ABC1234", "Fiat", "Uno", 2020);
		Filial filial = Filial.nova("Centro", "SP01", "Sao Paulo", "SP");
		OrdemServico ordemServico = OrdemServico.abrir(cliente, veiculo, filial, "Barulho no motor");
		UUID eventId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();

		JsonNode json = objectMapper.readTree(serializer.serialize(eventId, ordemServico, correlationId));

		assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
		assertThat(json.get("eventType").asText()).isEqualTo(OutboxEvent.DIAGNOSTIC_START_V1);
		assertThat(json.get("occurredAt").isTextual()).isTrue();
		assertThat(Instant.parse(json.get("occurredAt").asText())).isEqualTo(ordemServico.getCreatedAt());
		assertThat(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isTrue();
		assertThat(json.get("correlationId").asText()).isEqualTo(correlationId.toString());
		assertThat(json.get("sagaId").isNull()).isTrue();
		assertThat(json.get("osId").asText()).isEqualTo(ordemServico.getId().toString());
		assertThat(json.get("source").asText()).isEqualTo(OutboxEvent.SOURCE);
		assertThat(json.at("/payload/clienteId").asText()).isEqualTo(cliente.getId().toString());
		assertThat(json.at("/payload/observacoes").asText()).isEqualTo("Barulho no motor");
	}
}
