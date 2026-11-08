package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.HistoricoStatus;
import com.oficina.os.domain.OrdemServicoStatus;

import java.time.Instant;
import java.util.UUID;

public record HistoricoStatusResponse(UUID id, UUID ordemServicoId, OrdemServicoStatus statusAnterior,
		OrdemServicoStatus statusNovo, String motivo, Instant createdAt, UUID correlationId) {

	public static HistoricoStatusResponse from(HistoricoStatus historicoStatus) {
		return new HistoricoStatusResponse(historicoStatus.getId(), historicoStatus.getOrdemServicoId(),
				historicoStatus.getStatusAnterior(), historicoStatus.getStatusNovo(), historicoStatus.getMotivo(),
				historicoStatus.getCreatedAt(), historicoStatus.getCorrelationId());
	}
}
