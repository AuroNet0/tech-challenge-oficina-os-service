package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OrdemServicoStatus;
import java.time.Instant;
import java.util.UUID;

public record OperationalOrderResponse(UUID id, UUID veiculoId, UUID filialId, OrdemServicoStatus status,
                                       Instant startedAt, Instant finishedAt) {
    public static OperationalOrderResponse from(OrdemServico order) {
        return new OperationalOrderResponse(order.getId(), order.getVeiculoId(), order.getFilialId(),
                order.getStatus(), order.getStartedAt(), order.getFinishedAt());
    }
}
