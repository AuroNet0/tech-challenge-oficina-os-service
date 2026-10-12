package com.oficina.os.domain;

import com.oficina.os.domain.validation.TextNormalizer;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class OrdemServico {

	private static final Map<OrdemServicoStatus, OrdemServicoStatus> NORMAL_TRANSITIONS =
			new EnumMap<>(OrdemServicoStatus.class);
	private static final Set<OrdemServicoStatus> CANCELLABLE_STATUSES = EnumSet.of(
			OrdemServicoStatus.RECEBIDA,
			OrdemServicoStatus.EM_DIAGNOSTICO,
			OrdemServicoStatus.AGUARDANDO_APROVACAO);
	private static final Set<OrdemServicoStatus> TERMINAL_STATUSES = EnumSet.of(
			OrdemServicoStatus.ENTREGUE,
			OrdemServicoStatus.CANCELADA);

	static {
		NORMAL_TRANSITIONS.put(OrdemServicoStatus.RECEBIDA, OrdemServicoStatus.EM_DIAGNOSTICO);
		NORMAL_TRANSITIONS.put(OrdemServicoStatus.EM_DIAGNOSTICO, OrdemServicoStatus.AGUARDANDO_APROVACAO);
		NORMAL_TRANSITIONS.put(OrdemServicoStatus.AGUARDANDO_APROVACAO, OrdemServicoStatus.EM_EXECUCAO);
		NORMAL_TRANSITIONS.put(OrdemServicoStatus.EM_EXECUCAO, OrdemServicoStatus.AGUARDANDO_PAGAMENTO);
		NORMAL_TRANSITIONS.put(OrdemServicoStatus.AGUARDANDO_PAGAMENTO, OrdemServicoStatus.FINALIZADA);
		NORMAL_TRANSITIONS.put(OrdemServicoStatus.FINALIZADA, OrdemServicoStatus.ENTREGUE);
	}

	private final UUID id;
	private final UUID clienteId;
	private final UUID veiculoId;
	private final UUID filialId;
	private OrdemServicoStatus status;
	private final String observacoes;
	private final Instant createdAt;
	private Instant updatedAt;
	private Instant startedAt;
	private Instant finishedAt;
	private Long version;

	private OrdemServico(UUID id, UUID clienteId, UUID veiculoId, UUID filialId, OrdemServicoStatus status,
			String observacoes, Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
			Long version) {
		this.id = Objects.requireNonNull(id, "id is required");
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId is required");
		this.veiculoId = Objects.requireNonNull(veiculoId, "veiculoId is required");
		this.filialId = Objects.requireNonNull(filialId, "filialId is required");
		this.status = Objects.requireNonNull(status, "status is required");
		this.observacoes = TextNormalizer.optional(observacoes);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.version = version;
		validateTimestamps();
	}

	public static OrdemServico abrir(Cliente cliente, Veiculo veiculo, Filial filial, String observacoes) {
		Objects.requireNonNull(cliente, "cliente is required");
		Objects.requireNonNull(veiculo, "veiculo is required");
		Objects.requireNonNull(filial, "filial is required");
		if (!cliente.isAtivo()) {
			throw new DomainException("cliente must be active");
		}
		if (!filial.isAtivo()) {
			throw new DomainException("filial must be active");
		}
		if (!veiculo.pertenceAoCliente(cliente.getId())) {
			throw new DomainException("veiculo must belong to cliente");
		}
		Instant now = Instant.now();
		return new OrdemServico(UUID.randomUUID(), cliente.getId(), veiculo.getId(), filial.getId(),
				OrdemServicoStatus.RECEBIDA, observacoes, now, now, null, null, null);
	}

	public static OrdemServico restaurar(UUID id, UUID clienteId, UUID veiculoId, UUID filialId,
			OrdemServicoStatus status, String observacoes, Instant createdAt, Instant updatedAt,
			Instant startedAt, Instant finishedAt, Long version) {
		return new OrdemServico(id, clienteId, veiculoId, filialId, status, observacoes, createdAt, updatedAt,
				startedAt, finishedAt, version);
	}

	public HistoricoStatus registrarCriacao(String motivo, UUID correlationId) {
		return HistoricoStatus.registrarCriacao(id, status, motivo, correlationId);
	}

	public HistoricoStatus avancar(String motivo, UUID correlationId) {
		OrdemServicoStatus next = NORMAL_TRANSITIONS.get(status);
		if (next == null) {
			throw new DomainException("status does not allow a normal transition");
		}
		return transitionTo(next, motivo, correlationId);
	}

	public HistoricoStatus iniciarDiagnostico(String motivo, UUID correlationId) {
		return transitionTo(OrdemServicoStatus.EM_DIAGNOSTICO, motivo, correlationId);
	}

	public HistoricoStatus aguardarAprovacao(String motivo, UUID correlationId) {
		return transitionTo(OrdemServicoStatus.AGUARDANDO_APROVACAO, motivo, correlationId);
	}

	public HistoricoStatus iniciarExecucao(String motivo, UUID correlationId) {
		return transitionTo(OrdemServicoStatus.EM_EXECUCAO, motivo, correlationId);
	}

	public HistoricoStatus aguardarPagamento(String motivo, UUID correlationId) {
		return transitionTo(OrdemServicoStatus.AGUARDANDO_PAGAMENTO, motivo, correlationId);
	}

	public HistoricoStatus finalizar(String motivo, UUID correlationId) {
		return transitionTo(OrdemServicoStatus.FINALIZADA, motivo, correlationId);
	}

	public HistoricoStatus entregar(String motivo, UUID correlationId) {
		return transitionTo(OrdemServicoStatus.ENTREGUE, motivo, correlationId);
	}

	public HistoricoStatus cancelar(String motivo, UUID correlationId) {
		if (!CANCELLABLE_STATUSES.contains(status)) {
			throw new DomainException("ordemServico cannot be cancelled from status " + status);
		}
		return transitionTo(OrdemServicoStatus.CANCELADA, motivo, correlationId);
	}

	private HistoricoStatus transitionTo(OrdemServicoStatus newStatus, String motivo, UUID correlationId) {
		if (TERMINAL_STATUSES.contains(status)) {
			throw new DomainException("terminal ordemServico cannot be changed");
		}
		OrdemServicoStatus expected = NORMAL_TRANSITIONS.get(status);
		boolean isAllowedCancellation = newStatus == OrdemServicoStatus.CANCELADA && CANCELLABLE_STATUSES.contains(status);
		if (!isAllowedCancellation && expected != newStatus) {
			throw new DomainException("invalid status transition from " + status + " to " + newStatus);
		}

		OrdemServicoStatus previous = status;
		status = newStatus;
		Instant now = Instant.now();
		updatedAt = now;
		if (newStatus == OrdemServicoStatus.EM_EXECUCAO && startedAt == null) {
			startedAt = now;
		}
		if (newStatus == OrdemServicoStatus.FINALIZADA && finishedAt == null) {
			finishedAt = now;
		}
		return HistoricoStatus.registrarTransicao(id, previous, newStatus, motivo, correlationId);
	}

	private void validateTimestamps() {
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("updatedAt cannot be before createdAt");
		}
		if (finishedAt != null && status.ordinal() < OrdemServicoStatus.FINALIZADA.ordinal()
				&& status != OrdemServicoStatus.CANCELADA) {
			throw new IllegalArgumentException("finishedAt requires a finished status");
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getClienteId() {
		return clienteId;
	}

	public UUID getVeiculoId() {
		return veiculoId;
	}

	public UUID getFilialId() {
		return filialId;
	}

	public OrdemServicoStatus getStatus() {
		return status;
	}

	public String getObservacoes() {
		return observacoes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public Long getVersion() {
		return version;
	}
}
