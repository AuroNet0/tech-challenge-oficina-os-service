package com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "filiais")
class FilialJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false, unique = true)
	private String codigo;

	@Column(nullable = false)
	private String cidade;

	@Column(nullable = false, length = 2)
	private String uf;

	@Column(nullable = false)
	private boolean ativo;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected FilialJpaEntity() {
	}

	FilialJpaEntity(UUID id, String nome, String codigo, String cidade, String uf, boolean ativo,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.nome = nome;
		this.codigo = codigo;
		this.cidade = cidade;
		this.uf = uf;
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

	String getCodigo() {
		return codigo;
	}

	String getCidade() {
		return cidade;
	}

	String getUf() {
		return uf;
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
