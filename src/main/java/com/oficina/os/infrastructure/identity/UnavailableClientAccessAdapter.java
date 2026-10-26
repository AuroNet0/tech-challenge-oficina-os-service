package com.oficina.os.infrastructure.identity;

import com.oficina.os.application.exception.ClientAccessException;
import com.oficina.os.application.port.out.ClientAccessPort;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public final class UnavailableClientAccessAdapter implements ClientAccessPort {
    @Override
    public boolean verificar(UUID clienteId) {
        throw new ClientAccessException(ClientAccessException.Motivo.INDISPONIVEL);
    }
}
