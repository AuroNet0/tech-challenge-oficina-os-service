package com.oficina.os.infrastructure.security;

import com.oficina.os.application.authorization.*;
import com.oficina.os.application.port.out.AuthenticatedUserToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class HttpCurrentActor implements CurrentActor, AuthenticatedUserToken {
    private JwtAuthenticationToken authentication() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AuthorizationDenied();
        }
        return jwt;
    }

    @Override
    public Actor get() {
        var jwt = authentication();
        Set<String> roles = jwt.getAuthorities().stream().map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5)).collect(Collectors.toUnmodifiableSet());
        return new Actor(UUID.fromString(jwt.getToken().getSubject()), roles);
    }

    @Override
    public String obter() { return authentication().getToken().getTokenValue(); }
}
