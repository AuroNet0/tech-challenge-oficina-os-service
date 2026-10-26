package com.oficina.os.application.authorization;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Actor(UUID usuarioId, Set<String> roles) {
    public Actor {
        Objects.requireNonNull(usuarioId);
        roles = Set.copyOf(roles);
    }

    public boolean has(String role) { return roles.contains(role); }
}
