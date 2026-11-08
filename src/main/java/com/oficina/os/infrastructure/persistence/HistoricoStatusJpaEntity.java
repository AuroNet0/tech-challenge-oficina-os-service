package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.OrdemServicoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historico_status")
class HistoricoStatusJpaEntity {

	@Id
	private UUID id;

	@Column(name = "ordem_servico_id", nullable = false)
	private UUID ordemServicoId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status_anterior")
	private OrdemServicoStatus statusAnterior;

	@Enumerated(EnumType.STRING)
	@Column(name = "status_novo", nullable = false)
	private OrdemServicoStatus statusNovo;

	@Column(columnDefinition = "text")
	private String motivo;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "correlation_id")
	private UUID correlationId;

	protected HistoricoStatusJpaEntity() {
	}

	HistoricoStatusJpaEntity(UUID id, UUID ordemServicoId, OrdemServicoStatus statusAnterior,
			OrdemServicoStatus statusNovo, String motivo, Instant createdAt, UUID correlationId) {
		this.id = id;
		this.ordemServicoId = ordemServicoId;
		this.statusAnterior = statusAnterior;
		this.statusNovo = statusNovo;
		this.motivo = motivo;
		this.createdAt = createdAt;
		this.correlationId = correlationId;
	}

	UUID getId() {
		return id;
	}

	UUID getOrdemServicoId() {
		return ordemServicoId;
	}

	OrdemServicoStatus getStatusAnterior() {
		return statusAnterior;
	}

	OrdemServicoStatus getStatusNovo() {
		return statusNovo;
	}

	String getMotivo() {
		return motivo;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	UUID getCorrelationId() {
		return correlationId;
	}
}
