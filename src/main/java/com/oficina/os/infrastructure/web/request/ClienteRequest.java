package com.oficina.os.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;

public record ClienteRequest(
		@NotBlank String nome,
		@NotBlank String cpfCnpj,
		String email,
		String telefone) {
}
