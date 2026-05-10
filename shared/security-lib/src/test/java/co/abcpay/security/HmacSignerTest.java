package co.abcpay.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacSignerTest {

    private static final String SECRET = "test-secret";
    private static final String METHOD = "POST";
    private static final String PATH = "/api/payments";
    private static final String TS = "1700000000";
    private static final String IDEM = "abc-123";

    @Test
    void verifyAcceptsCorrectSignature() {
        byte[] body = "{\"amount\":\"10.00\"}".getBytes(StandardCharsets.UTF_8);
        String sig = HmacSigner.sign(METHOD, PATH, body, TS, IDEM, SECRET);
        assertTrue(HmacSigner.verify(METHOD, PATH, body, TS, IDEM, SECRET, sig));
    }

    @Test
    void verifyRejectsBitFlipInBody() {
        byte[] body = "{\"amount\":\"10.00\"}".getBytes(StandardCharsets.UTF_8);
        String sig = HmacSigner.sign(METHOD, PATH, body, TS, IDEM, SECRET);
        byte[] tampered = body.clone();
        tampered[3] ^= 0x01;
        assertFalse(HmacSigner.verify(METHOD, PATH, tampered, TS, IDEM, SECRET, sig));
    }

    @Test
    void verifyRejectsHeaderTamper() {
        byte[] body = "{\"amount\":\"10.00\"}".getBytes(StandardCharsets.UTF_8);
        String sig = HmacSigner.sign(METHOD, PATH, body, TS, IDEM, SECRET);
        assertFalse(HmacSigner.verify(METHOD, PATH, body, "1700000099", IDEM, SECRET, sig));
    }

    @Test
    void verifyRejectsSwappedBodyWithSameSig() {
        byte[] bodyA = "{\"amount\":\"10.00\"}".getBytes(StandardCharsets.UTF_8);
        byte[] bodyB = "{\"amount\":\"99.00\"}".getBytes(StandardCharsets.UTF_8);
        String sigForA = HmacSigner.sign(METHOD, PATH, bodyA, TS, IDEM, SECRET);
        assertFalse(HmacSigner.verify(METHOD, PATH, bodyB, TS, IDEM, SECRET, sigForA));
    }
}
