package com.oficina.os.infrastructure.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

final class SigV4TestVerifier {
    static final String ACCESS = "ASIAEXAMPLE0000000001";
    static final String SECRET = "synthetic-secret-for-local-tests-only";
    static final String SESSION = "synthetic-workload-session";

    static void verificar(Map<String, List<String>> source, byte[] body, Instant now) throws Exception {
        Map<String, String> headers = new TreeMap<>();
        source.forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values).strip().replaceAll("\\s+", " ")));
        var match = Pattern.compile("AWS4-HMAC-SHA256 Credential=([^/]+)/(\\d{8})/us-east-1/execute-api/aws4_request, SignedHeaders=([^,]+), Signature=([a-f0-9]{64})")
                .matcher(headers.getOrDefault("authorization", ""));
        if (!match.matches() || !ACCESS.equals(match.group(1)) || !SESSION.equals(headers.get("x-amz-security-token"))) {
            throw new SecurityException("Credencial de teste invalida");
        }
        Instant signedAt = LocalDateTime.parse(headers.get("x-amz-date"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
                .toInstant(ZoneOffset.UTC);
        if (Duration.between(signedAt, now).abs().compareTo(Duration.ofSeconds(60)) >= 0) { throw new SecurityException("Expirada"); }
        String digest = sha(body);
        if (!digest.equals(headers.get("x-amz-content-sha256"))) { throw new SecurityException("Corpo alterado"); }
        String signedHeaders = match.group(3);
        StringBuilder canonicalHeaders = new StringBuilder();
        for (String name : signedHeaders.split(";")) { canonicalHeaders.append(name).append(':').append(headers.get(name)).append('\n'); }
        String canonical = "POST\n" + SigV4ClientAccessSigner.PATH + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + digest;
        String scope = match.group(2) + "/us-east-1/execute-api/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + headers.get("x-amz-date") + "\n" + scope + "\n" + sha(canonical.getBytes(StandardCharsets.UTF_8));
        byte[] key = hmac(("AWS4" + SECRET).getBytes(StandardCharsets.UTF_8), match.group(2));
        key = hmac(key, "us-east-1");
        key = hmac(key, "execute-api");
        key = hmac(key, "aws4_request");
        if (!MessageDigest.isEqual(hmac(key, toSign), HexFormat.of().parseHex(match.group(4)))) {
            throw new SecurityException("Assinatura invalida");
        }
    }

    private static String sha(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    private static byte[] hmac(byte[] key, String value) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
