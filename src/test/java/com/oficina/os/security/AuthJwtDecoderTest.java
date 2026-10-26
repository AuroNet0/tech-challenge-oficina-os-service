package com.oficina.os.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.oficina.os.infrastructure.security.AuthJwtDecoder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AuthJwtDecoderTest {
    static final TestJwtIssuer issuer = new TestJwtIssuer();
    final UUID user = UUID.randomUUID();
    final AuthJwtDecoder decoder = new AuthJwtDecoder(TestJwtIssuer.ISSUER, "oficina", issuer.jwksUri(), Clock.systemUTC());

    @AfterAll
    static void stop() { issuer.close(); }

    @Test
    void acceptsRealRs256AndEmptyRoles() {
        assertThat(decoder.decode(issuer.token(user, "CLIENTE")).getSubject()).isEqualTo(user.toString());
        assertThat(decoder.decode(issuer.token(user)).getClaimAsStringList("roles")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "signature", "algorithm", "issuer", "audience", "subject", "jti", "roles",
            "missing-roles", "roles-string", "roles-number", "duplicate-role", "iat", "nbf", "ttl", "aud-string", "unsigned", "missing-exp"})
    void rejectsInvalidTokens(String scenario) throws Exception {
        var claims = issuer.claims(user, "CLIENTE");
        long now = Instant.now().getEpochSecond();
        switch (scenario) {
            case "expired" -> { claims.put("iat", now-1000); claims.put("nbf", now-1000); claims.put("exp", now-100); }
            case "issuer" -> claims.put("iss", "https://other.example.test");
            case "audience" -> claims.put("aud", List.of("other"));
            case "subject" -> claims.put("sub", "1-1-1-1-1");
            case "jti" -> claims.put("jti", "invalid");
            case "roles" -> claims.put("roles", List.of("ADMIN"));
            case "missing-roles" -> claims.remove("roles");
            case "roles-string" -> claims.put("roles", "GERENTE");
            case "roles-number" -> claims.put("roles", List.of(1));
            case "duplicate-role" -> claims.put("roles", List.of("CLIENTE", "CLIENTE"));
            case "iat" -> { claims.put("iat", now+60); claims.put("nbf", now+60); }
            case "nbf" -> claims.put("nbf", now+60);
            case "ttl" -> claims.put("exp", now+1000);
            case "aud-string" -> claims.put("aud", "oficina");
            case "missing-exp" -> claims.remove("exp");
        }
        String token = issuer.sign(claims, scenario.equals("signature") ? new RSAKeyGenerator(2048).generate() : issuer.key,
                scenario.equals("algorithm") ? JWSAlgorithm.RS384 : JWSAlgorithm.RS256);
        if (scenario.equals("unsigned")) { token = "eyJhbGciOiJub25lIn0." + token.split("\\.")[1] + "."; }
        String supplied = token;
        assertThatThrownBy(() -> decoder.decode(supplied)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingAndOversizedTokenAndUnavailableJwks() {
        assertThatThrownBy(() -> decoder.decode(null)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("a".repeat(8193))).isInstanceOf(JwtException.class);
        var unavailable = new AuthJwtDecoder(TestJwtIssuer.ISSUER, "oficina", "http://127.0.0.1:1/jwks", Clock.systemUTC());
        assertThatThrownBy(() -> unavailable.decode(issuer.token(user))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> new AuthJwtDecoder("", "", "", Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }
}
