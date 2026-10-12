package com.oficina.os.infrastructure.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AbrirOrdemServicoRequest(
		@NotNull UUID clienteId,
		@NotNull UUID veiculoId,
		@NotNull UUID filialId,
		String observacoes) {
}
