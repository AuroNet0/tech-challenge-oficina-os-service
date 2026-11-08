package com.oficina.os.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "veiculos")
class VeiculoJpaEntity {

	@Id
	private UUID id;

	@Column(name = "cliente_id", nullable = false)
	private UUID clienteId;

	@Column(nullable = false, unique = true)
	private String placa;

	private String marca;

	@Column(nullable = false)
	private String modelo;

	private Integer ano;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected VeiculoJpaEntity() {
	}

	VeiculoJpaEntity(UUID id, UUID clienteId, String placa, String marca, String modelo, Integer ano,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.clienteId = clienteId;
		this.placa = placa;
		this.marca = marca;
		this.modelo = modelo;
		this.ano = ano;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	UUID getId() {
		return id;
	}

	UUID getClienteId() {
		return clienteId;
	}

	String getPlaca() {
		return placa;
	}

	String getMarca() {
		return marca;
	}

	String getModelo() {
		return modelo;
	}

	Integer getAno() {
		return ano;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}
}
