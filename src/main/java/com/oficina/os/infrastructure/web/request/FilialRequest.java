package com.oficina.os.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;

public record FilialRequest(
		@NotBlank String nome,
		@NotBlank String codigo,
		@NotBlank String cidade,
		@NotBlank String uf) {
}
