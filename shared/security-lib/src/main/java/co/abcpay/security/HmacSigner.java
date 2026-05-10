package co.abcpay.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * HMAC-SHA256 signing/verification over a canonical string composed of the
 * HTTP method, path, raw body bytes (as-sent), timestamp, and idempotency key.
 * <p>
 * Constant-time comparison is used to avoid timing oracles even though this
 * is a demo project; it costs nothing and matches industry practice.
 */
public final class HmacSigner {

    private static final String ALGO = "HmacSHA256";

    private HmacSigner() {}

    public static String sign(String method,
                              String path,
                              byte[] body,
                              String timestamp,
                              String idempotencyKey,
                              String secret) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            mac.update(method.toUpperCase().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '\n');
            mac.update(path.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '\n');
            mac.update(body == null ? new byte[0] : body);
            mac.update((byte) '\n');
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '\n');
            mac.update(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return toHex(mac.doFinal());
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    public static boolean verify(String method,
                                 String path,
                                 byte[] body,
                                 String timestamp,
                                 String idempotencyKey,
                                 String secret,
                                 String providedSignatureHex) {
        if (providedSignatureHex == null) {
            return false;
        }
        String expected = sign(method, path, body, timestamp, idempotencyKey, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignatureHex.getBytes(StandardCharsets.UTF_8));
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
