package com.oficina.os.infrastructure.identity;

import com.oficina.os.application.exception.ClientAccessException;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.regions.Region;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import static com.oficina.os.application.exception.ClientAccessException.Motivo.*;

public final class SigV4ClientAccessSigner {
    public static final String PATH = "/internal/v1/identity/client-access/check";
    private final AwsCredentialsProvider credentials;
    private final Region region;
    private final Clock clock;

    public SigV4ClientAccessSigner(AwsCredentialsProvider credentials, Region region, Clock clock) {
        this.credentials = Objects.requireNonNull(credentials);
        this.region = Objects.requireNonNull(region);
        this.clock = Objects.requireNonNull(clock);
    }

    public SdkHttpFullRequest assinar(URI endpoint, byte[] body) {
        validarEndpoint(endpoint);
        try {
            if (body == null || body.length > 12288) { throw new ClientAccessException(RESPOSTA_INVALIDA); }
            var resolved = credentials.resolveCredentials();
            if (!(resolved instanceof AwsSessionCredentials session) || session.sessionToken().isBlank()
                    || session.expirationTime().filter(exp -> clock.instant().isBefore(exp)).isEmpty()) {
                throw new ClientAccessException(AUTENTICACAO);
            }
            byte[] payload = body.clone();
            var request = SdkHttpFullRequest.builder().uri(endpoint).method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("X-Amz-Content-Sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)))
                    .contentStreamProvider(() -> new ByteArrayInputStream(payload)).build();
            return Aws4Signer.create().sign(request, Aws4SignerParams.builder().awsCredentials(session)
                    .signingName("execute-api").signingRegion(region).signingClockOverride(clock).build());
        } catch (ClientAccessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ClientAccessException(INDISPONIVEL);
        }
    }

    static void validarEndpoint(URI endpoint) {
        if (endpoint == null || !"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
                || !PATH.equals(endpoint.getRawPath()) || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null || endpoint.getUserInfo() != null || endpoint.getPort() == 0) {
            throw new IllegalArgumentException("Endpoint interno HTTPS invalido");
        }
    }
}
