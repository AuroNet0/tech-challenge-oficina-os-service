package com.oficina.os.domain;

import com.oficina.os.domain.validation.CpfCnpj;
import com.oficina.os.domain.validation.TextNormalizer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Cliente {

	private final UUID id;
	private final String nome;
	private final String cpfCnpj;
	private final String email;
	private final String telefone;
	private final boolean ativo;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Cliente(UUID id, String nome, String cpfCnpj, String email, String telefone, boolean ativo,
			Instant createdAt, Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "id is required");
		this.nome = TextNormalizer.required(nome, "nome");
		String normalizedDocument = CpfCnpj.normalize(cpfCnpj);
		if (!CpfCnpj.isValid(normalizedDocument)) {
			throw new IllegalArgumentException("cpfCnpj is invalid");
		}
		this.cpfCnpj = normalizedDocument;
		this.email = TextNormalizer.optional(email);
		this.telefone = TextNormalizer.optional(telefone);
		this.ativo = ativo;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt cannot be before createdAt");
		}
	}

	public static Cliente novo(String nome, String cpfCnpj, String email, String telefone) {
		Instant now = Instant.now();
		return new Cliente(UUID.randomUUID(), nome, cpfCnpj, email, telefone, true, now, now);
	}

	public static Cliente restaurar(UUID id, String nome, String cpfCnpj, String email, String telefone,
			boolean ativo, Instant createdAt, Instant updatedAt) {
		return new Cliente(id, nome, cpfCnpj, email, telefone, ativo, createdAt, updatedAt);
	}

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getCpfCnpj() {
		return cpfCnpj;
	}

	public String getEmail() {
		return email;
	}

	public String getTelefone() {
		return telefone;
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
