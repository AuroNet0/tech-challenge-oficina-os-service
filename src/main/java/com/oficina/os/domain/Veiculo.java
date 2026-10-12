package com.oficina.os.domain;

import com.oficina.os.domain.validation.Placa;
import com.oficina.os.domain.validation.TextNormalizer;

import java.time.Instant;
import java.time.Year;
import java.util.Objects;
import java.util.UUID;

public class Veiculo {

	private static final int FIRST_CAR_YEAR = 1886;

	private final UUID id;
	private final UUID clienteId;
	private final String placa;
	private final String marca;
	private final String modelo;
	private final Integer ano;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Veiculo(UUID id, UUID clienteId, String placa, String marca, String modelo, Integer ano,
			Instant createdAt, Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "id is required");
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId is required");
		String normalizedPlate = Placa.normalize(placa);
		if (!Placa.isValid(normalizedPlate)) {
			throw new IllegalArgumentException("placa is invalid");
		}
		validateYear(ano);
		this.placa = normalizedPlate;
		this.marca = TextNormalizer.optional(marca);
		this.modelo = TextNormalizer.required(modelo, "modelo");
		this.ano = ano;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt cannot be before createdAt");
		}
	}

	public static Veiculo novo(UUID clienteId, String placa, String marca, String modelo, Integer ano) {
		Instant now = Instant.now();
		return new Veiculo(UUID.randomUUID(), clienteId, placa, marca, modelo, ano, now, now);
	}

	public static Veiculo restaurar(UUID id, UUID clienteId, String placa, String marca, String modelo, Integer ano,
			Instant createdAt, Instant updatedAt) {
		return new Veiculo(id, clienteId, placa, marca, modelo, ano, createdAt, updatedAt);
	}

	private static void validateYear(Integer ano) {
		if (ano == null) {
			return;
		}
		int maxYear = Year.now().getValue() + 1;
		if (ano < FIRST_CAR_YEAR || ano > maxYear) {
			throw new IllegalArgumentException("ano is outside the accepted range");
		}
	}

	public boolean pertenceAoCliente(UUID clienteId) {
		return this.clienteId.equals(clienteId);
	}

	public UUID getId() {
		return id;
	}

	public UUID getClienteId() {
		return clienteId;
	}

	public String getPlaca() {
		return placa;
	}

	public String getMarca() {
		return marca;
	}

	public String getModelo() {
		return modelo;
	}

	public Integer getAno() {
		return ano;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
