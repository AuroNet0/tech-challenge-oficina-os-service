package com.oficina.os.domain;

import com.oficina.os.domain.validation.TextNormalizer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class HistoricoStatus {

	private final UUID id;
	private final UUID ordemServicoId;
	private final OrdemServicoStatus statusAnterior;
	private final OrdemServicoStatus statusNovo;
	private final String motivo;
	private final Instant createdAt;
	private final UUID correlationId;

	private HistoricoStatus(UUID id, UUID ordemServicoId, OrdemServicoStatus statusAnterior,
			OrdemServicoStatus statusNovo, String motivo, Instant createdAt, UUID correlationId) {
		this.id = Objects.requireNonNull(id, "id is required");
		this.ordemServicoId = Objects.requireNonNull(ordemServicoId, "ordemServicoId is required");
		this.statusAnterior = statusAnterior;
		this.statusNovo = Objects.requireNonNull(statusNovo, "statusNovo is required");
		this.motivo = TextNormalizer.optional(motivo);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.correlationId = correlationId;
	}

	public static HistoricoStatus registrarCriacao(UUID ordemServicoId, OrdemServicoStatus statusNovo,
			String motivo, UUID correlationId) {
		return new HistoricoStatus(UUID.randomUUID(), ordemServicoId, null, statusNovo, motivo, Instant.now(), correlationId);
	}

	public static HistoricoStatus registrarTransicao(UUID ordemServicoId, OrdemServicoStatus statusAnterior,
			OrdemServicoStatus statusNovo, String motivo, UUID correlationId) {
		return new HistoricoStatus(UUID.randomUUID(), ordemServicoId, statusAnterior, statusNovo, motivo, Instant.now(), correlationId);
	}

	public static HistoricoStatus restaurar(UUID id, UUID ordemServicoId, OrdemServicoStatus statusAnterior,
			OrdemServicoStatus statusNovo, String motivo, Instant createdAt, UUID correlationId) {
		return new HistoricoStatus(id, ordemServicoId, statusAnterior, statusNovo, motivo, createdAt, correlationId);
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrdemServicoId() {
		return ordemServicoId;
	}

	public OrdemServicoStatus getStatusAnterior() {
		return statusAnterior;
	}

	public OrdemServicoStatus getStatusNovo() {
		return statusNovo;
	}

	public String getMotivo() {
		return motivo;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public UUID getCorrelationId() {
		return correlationId;
	}
}
