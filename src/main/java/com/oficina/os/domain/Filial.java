package com.oficina.os.domain;

import com.oficina.os.domain.validation.TextNormalizer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Filial {

	private final UUID id;
	private final String nome;
	private final String codigo;
	private final String cidade;
	private final String uf;
	private final boolean ativo;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Filial(UUID id, String nome, String codigo, String cidade, String uf, boolean ativo,
			Instant createdAt, Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "id is required");
		this.nome = TextNormalizer.required(nome, "nome");
		this.codigo = TextNormalizer.code(codigo, "codigo");
		this.cidade = TextNormalizer.required(cidade, "cidade");
		this.uf = TextNormalizer.uf(uf);
		this.ativo = ativo;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt cannot be before createdAt");
		}
	}

	public static Filial nova(String nome, String codigo, String cidade, String uf) {
		Instant now = Instant.now();
		return new Filial(UUID.randomUUID(), nome, codigo, cidade, uf, true, now, now);
	}

	public static Filial restaurar(UUID id, String nome, String codigo, String cidade, String uf,
			boolean ativo, Instant createdAt, Instant updatedAt) {
		return new Filial(id, nome, codigo, cidade, uf, ativo, createdAt, updatedAt);
	}

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getCidade() {
		return cidade;
	}

	public String getUf() {
		return uf;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
