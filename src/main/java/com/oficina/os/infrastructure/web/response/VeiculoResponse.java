package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.Veiculo;

import java.time.Instant;
import java.util.UUID;

public record VeiculoResponse(UUID id, UUID clienteId, String placa, String marca, String modelo,
		Integer ano, Instant createdAt, Instant updatedAt) {

	public static VeiculoResponse from(Veiculo veiculo) {
		return new VeiculoResponse(veiculo.getId(), veiculo.getClienteId(), veiculo.getPlaca(), veiculo.getMarca(),
				veiculo.getModelo(), veiculo.getAno(), veiculo.getCreatedAt(), veiculo.getUpdatedAt());
	}
}
