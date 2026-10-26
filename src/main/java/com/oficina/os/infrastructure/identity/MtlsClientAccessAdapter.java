package com.oficina.os.infrastructure.identity;

import com.oficina.os.application.exception.ClientAccessException;
import com.oficina.os.application.port.out.ClientAccessPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("mtls")
public final class MtlsClientAccessAdapter implements ClientAccessPort {
    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public MtlsClientAccessAdapter(@Value("${oficina.internal.auth-uri}") URI endpoint) {
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !"/internal/v1/identity/client-access/check".equals(endpoint.getPath())) {
            throw new IllegalArgumentException("Trusted HTTPS identity endpoint required");
        }
        this.endpoint = endpoint;
    }

    @Override
    public boolean verificar(UUID clienteId) {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken user) || !user.isAuthenticated()) {
                throw new ClientAccessException(ClientAccessException.Motivo.AUTENTICACAO);
            }
            var body = json.writeValueAsString(Map.of("clienteId", clienteId.toString(), "userAccessToken",
                    user.getToken().getTokenValue(), "requestId", UUID.randomUUID().toString(), "issuedAt", Instant.now().toString()));
            var response = http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !"no-store".equals(response.headers().firstValue("Cache-Control").orElse(""))
                    || response.body().length() > 1024) { throw new IllegalStateException(); }
            var result = json.readTree(response.body());
            if (!result.isObject() || result.size() != 1 || !result.path("autorizado").isBoolean()) {
                throw new IllegalStateException();
            }
            return result.get("autorizado").booleanValue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClientAccessException(ClientAccessException.Motivo.INDISPONIVEL);
        } catch (Exception exception) {
            throw new ClientAccessException(ClientAccessException.Motivo.INDISPONIVEL);
        }
    }
}
