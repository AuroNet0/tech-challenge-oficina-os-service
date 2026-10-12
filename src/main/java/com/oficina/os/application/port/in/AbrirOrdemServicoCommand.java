package com.oficina.os.application.port.in;

import java.util.UUID;

public record AbrirOrdemServicoCommand(UUID clienteId, UUID veiculoId, UUID filialId, String observacoes) {
}
