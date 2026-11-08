package com.oficina.os.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VeiculoRequest(
		@NotNull UUID clienteId,
		@NotBlank String placa,
		String marca,
		@NotBlank String modelo,
		Integer ano) {
}
