package com.oficina.os.application.port.in;

import java.util.UUID;

public record VeiculoCommand(UUID clienteId, String placa, String marca, String modelo, Integer ano) {
}
