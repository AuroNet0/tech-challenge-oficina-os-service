package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OrdemServicoStatus;

import java.time.Instant;
import java.util.UUID;

public record OrdemServicoResponse(UUID id, UUID clienteId, UUID veiculoId, UUID filialId,
		OrdemServicoStatus status, String observacoes, Instant createdAt, Instant updatedAt,
		Instant startedAt, Instant finishedAt, Long version) {

	public static OrdemServicoResponse from(OrdemServico ordemServico) {
		return new OrdemServicoResponse(ordemServico.getId(), ordemServico.getClienteId(), ordemServico.getVeiculoId(),
				ordemServico.getFilialId(), ordemServico.getStatus(), ordemServico.getObservacoes(),
				ordemServico.getCreatedAt(), ordemServico.getUpdatedAt(), ordemServico.getStartedAt(),
				ordemServico.getFinishedAt(), ordemServico.getVersion());
	}
}
