package com.oficina.os.application.authorization;

import com.oficina.os.application.port.out.ClientAccessPort;
import com.oficina.os.domain.OrdemServico;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ResourceAuthorization {
    private final OperationalAccessPort grants;
    private final ClientAccessPort clients;

    public ResourceAuthorization(OperationalAccessPort grants, ClientAccessPort clients) {
        this.grants = grants;
        this.clients = clients;
    }

    public void role(Actor actor, String... roles) {
        for (String role : roles) { if (actor.has(role)) { return; } }
        throw new AuthorizationDenied();
    }

    public void branch(Actor actor, UUID filialId, Capability capability, String... roles) {
        role(actor, roles);
        if (!grants.branch(actor.usuarioId(), filialId, capability)) { throw new AuthorizationDenied(); }
    }

    public void order(Actor actor, OrdemServico order) {
        if (actor.has("ATENDENTE") || actor.has("GERENTE")) {
            branch(actor, order.getFilialId(), Capability.OS_READ, "ATENDENTE", "GERENTE");
        } else {
            client(actor, order.getClienteId());
        }
    }

    public void client(Actor actor, UUID clienteId) {
        role(actor, "CLIENTE");
        if (!clients.verificar(clienteId)) { throw new AuthorizationDenied(); }
    }

    public void mechanic(Actor actor, UUID osId, Capability capability) {
        role(actor, "MECANICO");
        if (!grants.assigned(actor.usuarioId(), osId, capability)) { throw new AuthorizationDenied(); }
    }

    public void administrative(Actor actor, Capability capability) {
        role(actor, "GERENTE");
        if (!grants.administrative(actor.usuarioId(), capability)) { throw new AuthorizationDenied(); }
    }
}
