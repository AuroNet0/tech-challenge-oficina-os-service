package com.oficina.os.security;

import com.oficina.os.application.authorization.AuthorizationDenied;
import com.oficina.os.infrastructure.security.HttpCurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HttpCurrentActorTest {
    final HttpCurrentActor adapter = new HttpCurrentActor();

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void refusesAbsentOrNonJwtAuthentication() {
        assertThatThrownBy(adapter::obter).isInstanceOf(AuthorizationDenied.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user", "secret", List.of()));
        assertThatThrownBy(adapter::get).isInstanceOf(AuthorizationDenied.class);
    }

    @Test
    void extractsOnlyValidatedCurrentJwtAndMapsNoRoleHierarchyOrScopes() {
        UUID user = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("original.signed.jwt").header("alg", "RS256").subject(user.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("scope", "ADMIN").claim("roles", List.of("GERENTE", "MECANICO")).build();
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        SecurityContextHolder.getContext().setAuthentication(converter.convert(jwt));
        assertThat(adapter.get().usuarioId()).isEqualTo(user);
        assertThat(adapter.get().roles()).containsExactlyInAnyOrder("GERENTE", "MECANICO");
        assertThat(adapter.obter()).isEqualTo("original.signed.jwt");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(adapter::obter).isInstanceOf(AuthorizationDenied.class);
    }
}
