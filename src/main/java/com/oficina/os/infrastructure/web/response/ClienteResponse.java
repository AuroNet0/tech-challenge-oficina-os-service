package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.Cliente;

import java.time.Instant;
import java.util.UUID;

public record ClienteResponse(UUID id, String nome, String cpfCnpj, String email, String telefone,
		boolean ativo, Instant createdAt, Instant updatedAt) {

	public static ClienteResponse from(Cliente cliente) {
		return new ClienteResponse(cliente.getId(), cliente.getNome(), cliente.getCpfCnpj(), cliente.getEmail(),
				cliente.getTelefone(), cliente.isAtivo(), cliente.getCreatedAt(), cliente.getUpdatedAt());
	}
}
