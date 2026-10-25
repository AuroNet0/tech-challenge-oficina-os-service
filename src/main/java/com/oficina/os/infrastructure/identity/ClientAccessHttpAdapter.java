package com.oficina.os.infrastructure.identity;

import com.oficina.os.application.exception.ClientAccessException;
import com.oficina.os.application.port.out.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static com.oficina.os.application.exception.ClientAccessException.Motivo.*;

public final class ClientAccessHttpAdapter implements ClientAccessPort {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final URI endpoint;
    private final HttpClient http;
    private final SigV4ClientAccessSigner signer;
    private final AuthenticatedUserToken userToken;
    private final Clock clock;
    private final Duration timeout;

    public ClientAccessHttpAdapter(URI endpoint, HttpClient http, SigV4ClientAccessSigner signer,
                                   AuthenticatedUserToken userToken, Clock clock, Duration timeout) {
        SigV4ClientAccessSigner.validarEndpoint(endpoint);
        if (http.followRedirects() != HttpClient.Redirect.NEVER || http.connectTimeout().isEmpty()
                || timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("Timeout ou politica HTTP invalida");
        }
        this.endpoint = endpoint;
        this.http = http;
        this.signer = Objects.requireNonNull(signer);
        this.userToken = Objects.requireNonNull(userToken);
        this.clock = Objects.requireNonNull(clock);
        this.timeout = timeout;
    }

    @Override
    public boolean verificar(UUID clienteId) {
        Objects.requireNonNull(clienteId);
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            String token = userToken.obter();
            if (token == null || token.length() > 8192 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
                throw new ClientAccessException(AUTENTICACAO);
            }
            byte[] body = JSON.writeValueAsBytes(Map.of("clienteId", clienteId.toString(), "userAccessToken", token,
                    "requestId", UUID.randomUUID().toString(), "issuedAt", clock.instant().toString()));
            var signed = signer.assinar(endpoint, body);
            var builder = HttpRequest.newBuilder(endpoint).timeout(timeout).POST(HttpRequest.BodyPublishers.ofByteArray(body));
            signed.headers().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Host")) { values.forEach(value -> builder.header(name, value)); }
            });
            pending = http.sendAsync(builder.build(), ignored -> new LimitedBody());
            var response = pending.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            if (response.statusCode() == 401) { throw new ClientAccessException(AUTENTICACAO); }
            if (response.statusCode() == 403) { throw new ClientAccessException(SERVICO_NAO_AUTORIZADO); }
            if (response.statusCode() != 200) { throw new ClientAccessException(INDISPONIVEL); }
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT)
                    .matches("application/json(?:;.*)?") || response.headers().allValues("Cache-Control").stream()
                    .flatMap(value -> Arrays.stream(value.split(","))).noneMatch(value -> value.strip().equalsIgnoreCase("no-store"))) {
                throw new ClientAccessException(RESPOSTA_INVALIDA);
            }
            var result = JSON.readTree(response.body());
            if (result == null || !result.isObject() || result.size() != 1 || !result.path("autorizado").isBoolean()) {
                throw new ClientAccessException(RESPOSTA_INVALIDA);
            }
            return result.get("autorizado").booleanValue();
        } catch (ClientAccessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClientAccessException(INDISPONIVEL);
        } catch (Exception exception) {
            throw new ClientAccessException(INDISPONIVEL);
        } finally {
            if (pending != null && !pending.isDone()) { pending.cancel(true); }
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(Long.MAX_VALUE); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 1024 - body.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalArgumentException("Resposta interna excede limite"));
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                body.writeBytes(bytes);
            }
        }
        public void onError(Throwable error) { result.completeExceptionally(new IllegalStateException("Falha HTTP interna")); }
        public void onComplete() { result.complete(body.toByteArray()); }
    }
}
