package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.Filial;

import java.time.Instant;
import java.util.UUID;

public record FilialResponse(UUID id, String nome, String codigo, String cidade, String uf,
		boolean ativo, Instant createdAt, Instant updatedAt) {

	public static FilialResponse from(Filial filial) {
		return new FilialResponse(filial.getId(), filial.getNome(), filial.getCodigo(), filial.getCidade(),
				filial.getUf(), filial.isAtivo(), filial.getCreatedAt(), filial.getUpdatedAt());
	}
}
