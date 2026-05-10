package co.abcpay.experiments;

import co.abcpay.security.Canonicalizer;
import co.abcpay.security.HmacSigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Client used by the harness to drive the gateway. It exposes the primitives
 * needed by the A scenarios: sign correctly, sign-then-tamper, replay, etc.
 */
public class SigningClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String gatewayUrl;
    private final String secret;

    public SigningClient(String gatewayUrl, String secret) {
        this.gatewayUrl = gatewayUrl;
        this.secret = secret;
    }

    /** A5 control: sign correctly and send the same body that was signed. */
    public HttpResponse<String> sendValid(Object payload, String idemKey) throws Exception {
        byte[] body = Canonicalizer.canonicalize(payload);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = HmacSigner.sign("POST", "/api/payments", body, ts, idemKey, secret);
        return send(body, ts, idemKey, sig);
    }

    /** A1: flip one bit in the body after signing. */
    public HttpResponse<String> sendBitFlipped(Object payload, String idemKey) throws Exception {
        byte[] body = Canonicalizer.canonicalize(payload);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = HmacSigner.sign("POST", "/api/payments", body, ts, idemKey, secret);
        byte[] tampered = body.clone();
        tampered[3] ^= 0x01;
        return send(tampered, ts, idemKey, sig);
    }

    /** A2: change a header that is included in the signature. */
    public HttpResponse<String> sendHeaderTampered(Object payload, String idemKey) throws Exception {
        byte[] body = Canonicalizer.canonicalize(payload);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = HmacSigner.sign("POST", "/api/payments", body, ts, idemKey, secret);
        String tamperedTs = String.valueOf(Long.parseLong(ts) + 1);
        return send(body, tamperedTs, idemKey, sig);
    }

    /** A3: apply a valid signature for body X to a different body Y. */
    public HttpResponse<String> sendSignatureSwap(Object signedPayload,
                                                  Object sentPayload,
                                                  String idemKey) throws Exception {
        byte[] signedBody = Canonicalizer.canonicalize(signedPayload);
        byte[] sentBody = Canonicalizer.canonicalize(sentPayload);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = HmacSigner.sign("POST", "/api/payments", signedBody, ts, idemKey, secret);
        return send(sentBody, ts, idemKey, sig);
    }

    /** A4: replay with a stale timestamp that is outside the allowed window. */
    public HttpResponse<String> sendStaleReplay(Object payload, String idemKey, long staleSecondsAgo) throws Exception {
        byte[] body = Canonicalizer.canonicalize(payload);
        String ts = String.valueOf(Instant.now().getEpochSecond() - staleSecondsAgo);
        String sig = HmacSigner.sign("POST", "/api/payments", body, ts, idemKey, secret);
        return send(body, ts, idemKey, sig);
    }

    private HttpResponse<String> send(byte[] body, String ts, String idemKey, String sig) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/api/payments"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Signature", sig)
                .header("X-Timestamp", ts)
                .header("X-Idempotency-Key", idemKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
