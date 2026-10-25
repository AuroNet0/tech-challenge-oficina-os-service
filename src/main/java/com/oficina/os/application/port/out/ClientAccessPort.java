package com.oficina.os.application.port.out;

import java.util.UUID;

public interface ClientAccessPort {
    boolean verificar(UUID clienteId);
}
