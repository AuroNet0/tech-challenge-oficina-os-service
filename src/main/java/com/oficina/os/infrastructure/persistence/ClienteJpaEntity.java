package com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clientes")
class ClienteJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String nome;

	@Column(name = "cpf_cnpj", nullable = false, unique = true)
	private String cpfCnpj;

	private String email;

	private String telefone;

	@Column(nullable = false)
	private boolean ativo;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ClienteJpaEntity() {
	}

	ClienteJpaEntity(UUID id, String nome, String cpfCnpj, String email, String telefone, boolean ativo,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.nome = nome;
		this.cpfCnpj = cpfCnpj;
		this.email = email;
		this.telefone = telefone;
		this.ativo = ativo;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	UUID getId() {
		return id;
	}

	String getNome() {
		return nome;
	}

	String getCpfCnpj() {
		return cpfCnpj;
	}

	String getEmail() {
		return email;
	}

	String getTelefone() {
		return telefone;
	}

	boolean isAtivo() {
		return ativo;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}
}
