package com.oficina.os.domain;

import java.util.UUID;

public record DiagnosticStartPayload(UUID clienteId, UUID veiculoId, UUID filialId, String observacoes) {
}
