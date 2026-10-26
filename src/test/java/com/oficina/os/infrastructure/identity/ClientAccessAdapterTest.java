package com.oficina.os.infrastructure.identity;

import com.oficina.os.application.exception.ClientAccessException;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.oficina.os.application.exception.ClientAccessException.Motivo.*;
import static com.oficina.os.infrastructure.identity.SigV4TestVerifier.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientAccessAdapterTest {
    @TempDir
    static Path directory;
    private static SSLContext tls;
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.c2lnbmF0dXJl";

    @BeforeAll
    static void tls() throws Exception {
        Path store = directory.resolve("test.p12");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "local", "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", "test-password", "-keypass", "test-password",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(store)) { keys.load(input, "test-password".toCharArray()); }
        var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, "test-password".toCharArray());
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(keys);
        tls = SSLContext.getInstance("TLS");
        tls.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
    }

    private static AwsSessionCredentials credentials() {
        return AwsSessionCredentials.builder().accessKeyId(ACCESS).secretAccessKey(SECRET).sessionToken(SESSION)
                .expirationTime(NOW.plusSeconds(300)).build();
    }

    private static SigV4ClientAccessSigner signer(AwsCredentialsProvider credentials) {
        return new SigV4ClientAccessSigner(credentials, Region.US_EAST_1, CLOCK);
    }

    @Test
    void sdkAssinaCorpoETokenEVerificadorIndependenteDetectaAdulteracao() throws Exception {
        byte[] body = ("{\"clienteId\":\"alvo-original\",\"userAccessToken\":\"" + TOKEN + "\"}").getBytes(StandardCharsets.UTF_8);
        var signed = signer(() -> credentials()).assinar(URI.create("https://auth.example.test" + SigV4ClientAccessSigner.PATH), body);
        verificar(signed.headers(), body, NOW);
        verificar(signed.headers(), body, NOW);
        for (byte[] changed : new byte[][]{
                new String(body, StandardCharsets.UTF_8).replace("alvo-original", "outro-cliente").getBytes(StandardCharsets.UTF_8),
                new String(body, StandardCharsets.UTF_8).replace(TOKEN, "outro.jwt.token").getBytes(StandardCharsets.UTF_8),
                "{}".getBytes(StandardCharsets.UTF_8)}) {
            assertThrows(SecurityException.class, () -> verificar(signed.headers(), changed, NOW));
        }
        assertThrows(SecurityException.class, () -> verificar(signed.headers(), body, NOW.plusSeconds(60)));
        var headers = new HashMap<>(signed.headers());
        headers.replaceAll((name, values) -> name.equalsIgnoreCase("Authorization") ? List.of("invalid") : values);
        assertThrows(SecurityException.class, () -> verificar(headers, body, NOW));
        assertThrows(SecurityException.class, () -> verificar(Map.of(), body, NOW));
        var unknown = credentials().toBuilder().accessKeyId("ASIAUNKNOWN000000001").build();
        var unknownRequest = signer(() -> unknown).assinar(URI.create("https://auth.example.test" + SigV4ClientAccessSigner.PATH), body);
        assertThrows(SecurityException.class, () -> verificar(unknownRequest.headers(), body, NOW));
        var wrongSecret = credentials().toBuilder().secretAccessKey("wrong-secret").build();
        var invalid = signer(() -> wrongSecret).assinar(URI.create("https://auth.example.test" + SigV4ClientAccessSigner.PATH), body);
        assertThrows(SecurityException.class, () -> verificar(invalid.headers(), body, NOW));
    }

    @Test
    void rejeitaCredencialPermanenteSemPrazoExpiradaEEndpointInseguro() {
        URI endpoint = URI.create("https://auth.example.test" + SigV4ClientAccessSigner.PATH);
        for (AwsCredentials value : new AwsCredentials[]{AwsBasicCredentials.create("fake", "fake"),
                AwsSessionCredentials.create(ACCESS, SECRET, SESSION), credentials().toBuilder().expirationTime(NOW).build()}) {
            assertEquals(AUTENTICACAO, assertThrows(ClientAccessException.class, () -> signer(() -> value).assinar(endpoint, new byte[0])).motivo());
        }
        assertThrows(IllegalArgumentException.class, () -> signer(() -> credentials()).assinar(URI.create("http://localhost" + SigV4ClientAccessSigner.PATH), new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> signer(() -> credentials()).assinar(URI.create(endpoint + "?token=x"), new byte[0]));
        assertThrows(ClientAccessException.class, () -> signer(() -> credentials()).assinar(endpoint, new byte[12289]));
        assertEquals(INDISPONIVEL, assertThrows(ClientAccessException.class,
                () -> signer(() -> { throw new IllegalStateException("sensitive-provider-error"); }).assinar(endpoint, new byte[0])).motivo());
    }

    @Test
    void httpsRealPreservaEnvelopeAssinadoERefazConsultaSemCache() throws Exception {
        var calls = new AtomicInteger();
        var error = new AtomicReference<Throwable>();
        var targets = new CopyOnWriteArrayList<String>();
        withServer(exchange -> {
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                verificar(exchange.getRequestHeaders(), body, NOW);
                String request = new String(body, StandardCharsets.UTF_8);
                assertTrue(request.contains(TOKEN));
                assertFalse(request.contains("usuarioId"));
                targets.add(request);
                int index = calls.incrementAndGet();
                respond(exchange, 200, "{\"autorizado\":" + (index == 1) + "}");
            } catch (Throwable exception) { error.set(exception); respond(exchange, 403, "{}"); }
        }, endpoint -> {
            var adapter = adapter(endpoint, () -> TOKEN, Duration.ofSeconds(5));
            UUID cliente = UUID.randomUUID();
            assertTrue(adapter.verificar(cliente));
            assertFalse(adapter.verificar(cliente));
            assertEquals(2, calls.get());
            assertNotEquals(targets.get(0), targets.get(1));
            assertNull(error.get());
        });
    }

    @Test
    void transportePreservaStatusDeAutenticacao() throws Exception {
        withServer(exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 401, "{}");
        }, endpoint -> {
            var client = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(2)).build();
            var signed = signer(() -> credentials()).assinar(endpoint, "{}".getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"));
            signed.headers().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Host")) { values.forEach(value -> request.header(name, value)); }
            });
            try {
                assertEquals(401, client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            } catch (Exception exception) { throw new AssertionError(exception); }
        });
    }

    @Test
    void errosHttpRespostaInvalidaLimitesETimeoutNuncaViraramFalse() throws Exception {
        var status = new AtomicInteger(401);
        var body = new AtomicReference<>("{}");
        var delay = new AtomicBoolean();
        withServer(exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (delay.get()) { try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
            respond(exchange, status.get(), body.get());
        }, endpoint -> {
            var adapter = adapter(endpoint, () -> TOKEN, Duration.ofSeconds(5));
            UUID cliente = UUID.randomUUID();
            assertEquals(AUTENTICACAO, assertThrows(ClientAccessException.class, () -> adapter.verificar(cliente)).motivo());
            status.set(403);
            assertEquals(SERVICO_NAO_AUTORIZADO, assertThrows(ClientAccessException.class, () -> adapter.verificar(cliente)).motivo());
            status.set(503);
            assertEquals(INDISPONIVEL, assertThrows(ClientAccessException.class, () -> adapter.verificar(cliente)).motivo());
            status.set(200);
            for (String invalid : List.of("{}", "null", "{\"autorizado\":\"true\"}", "{\"autorizado\":true,\"email\":\"private\"}",
                    "{\"autorizado\":true,\"autorizado\":false}", "{\"autorizado\":true}{}", "x".repeat(1025))) {
                body.set(invalid);
                var exception = assertThrows(ClientAccessException.class, () -> adapter.verificar(cliente));
                assertFalse(exception.toString().contains(TOKEN));
                assertNull(exception.getCause());
            }
            delay.set(true);
            assertEquals(INDISPONIVEL, assertThrows(ClientAccessException.class,
                    () -> adapter(endpoint, () -> TOKEN, Duration.ofMillis(40)).verificar(cliente)).motivo());
        });
    }

    @Test
    void recusaContextoAusenteAntesDaRedeSemParametroParaSubstituirToken() {
        var calls = new AtomicInteger();
        URI endpoint = URI.create("https://auth.example.test" + SigV4ClientAccessSigner.PATH);
        var signer = signer(() -> { calls.incrementAndGet(); return credentials(); });
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        for (String token : new String[]{null, "", "bad", "x".repeat(8193)}) {
            var adapter = new ClientAccessHttpAdapter(endpoint, http, signer, () -> token, CLOCK, Duration.ofSeconds(1));
            assertEquals(AUTENTICACAO, assertThrows(ClientAccessException.class, () -> adapter.verificar(UUID.randomUUID())).motivo());
        }
        assertEquals(0, calls.get());
        assertThrows(IllegalArgumentException.class, () -> new ClientAccessHttpAdapter(endpoint,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), signer, () -> TOKEN, CLOCK, Duration.ofSeconds(1)));
    }

    private static ClientAccessHttpAdapter adapter(URI endpoint, com.oficina.os.application.port.out.AuthenticatedUserToken token, Duration timeout) {
        return new ClientAccessHttpAdapter(endpoint, HttpClient.newBuilder().sslContext(tls)
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
                signer(() -> credentials()), token, CLOCK, timeout);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static void withServer(HttpHandler handler, java.util.function.Consumer<URI> test) throws Exception {
        var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(executor);
            server.createContext(SigV4ClientAccessSigner.PATH, handler);
            server.start();
            try { test.accept(URI.create("https://127.0.0.1:" + server.getAddress().getPort() + SigV4ClientAccessSigner.PATH)); }
            finally { server.stop(0); }
        }
    }
}
