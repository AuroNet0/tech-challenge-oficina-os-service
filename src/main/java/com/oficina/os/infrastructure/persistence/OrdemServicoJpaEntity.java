package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.OrdemServicoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ordens_servico")
class OrdemServicoJpaEntity {

	@Id
	private UUID id;

	@Column(name = "cliente_id", nullable = false)
	private UUID clienteId;

	@Column(name = "veiculo_id", nullable = false)
	private UUID veiculoId;

	@Column(name = "filial_id", nullable = false)
	private UUID filialId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrdemServicoStatus status;

	@Column(columnDefinition = "text")
	private String observacoes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Version
	@Column(nullable = false)
	private Long version;

	protected OrdemServicoJpaEntity() {
	}

	OrdemServicoJpaEntity(UUID id, UUID clienteId, UUID veiculoId, UUID filialId, OrdemServicoStatus status,
			String observacoes, Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
			Long version) {
		this.id = id;
		this.clienteId = clienteId;
		this.veiculoId = veiculoId;
		this.filialId = filialId;
		this.status = status;
		this.observacoes = observacoes;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.version = version;
	}

	UUID getId() {
		return id;
	}

	UUID getClienteId() {
		return clienteId;
	}

	UUID getVeiculoId() {
		return veiculoId;
	}

	UUID getFilialId() {
		return filialId;
	}

	OrdemServicoStatus getStatus() {
		return status;
	}

	String getObservacoes() {
		return observacoes;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	Instant getStartedAt() {
		return startedAt;
	}

	Instant getFinishedAt() {
		return finishedAt;
	}

	Long getVersion() {
		return version;
	}
}
