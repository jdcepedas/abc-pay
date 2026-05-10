package co.abcpay.validator;

import co.abcpay.security.HmacSigner;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Implements the "Verify Message Integrity" tactic.
 * The gateway forwards the raw body bytes plus the headers that were included
 * in the signature; the validator recomputes the HMAC and rejects on mismatch
 * or replay (timestamp outside allowed skew).
 */
@RestController
@RequestMapping("/api/validator")
@Tag(name = "Signature Validator", description = "External authorization service that verifies HMAC signatures")
public class ValidationController {

    private static final String METRIC_TOTAL = "abcpay.validation.total";
    private static final String METRIC_LATENCY = "abcpay.validation.latency";

    private final String secret;
    private final long maxSkewSeconds;
    private final MeterRegistry meterRegistry;
    private final Timer latencyTimer;

    public ValidationController(@Value("${abcpay.shared-secret}") String secret,
                                @Value("${abcpay.max-skew-seconds:300}") long maxSkewSeconds,
                                MeterRegistry meterRegistry) {
        this.secret = secret;
        this.maxSkewSeconds = maxSkewSeconds;
        this.meterRegistry = meterRegistry;
        this.latencyTimer = Timer.builder(METRIC_LATENCY)
                .description("HMAC verification latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Operation(
            summary = "Verify a signed request",
            description = "Recomputes HMAC-SHA256(method|path|body|timestamp|idempotencyKey) and compares with the provided signature using a constant-time check. Also enforces a timestamp skew window to defeat replay attacks.")
    @ApiResponse(responseCode = "200", description = "Verdict: { valid: true } or { valid: false, reason }")
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody VerifyRequest req) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return doVerify(req);
        } finally {
            sample.stop(latencyTimer);
        }
    }

    private Map<String, Object> doVerify(VerifyRequest req) {
        if (req.method() == null || req.path() == null
                || req.timestamp() == null || req.idempotencyKey() == null
                || req.signature() == null) {
            return reject("missing_required_fields");
        }

        long ts;
        try {
            ts = Long.parseLong(req.timestamp());
        } catch (NumberFormatException e) {
            return reject("invalid_timestamp_format");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > maxSkewSeconds) {
            recordReject("timestamp_outside_skew_window");
            return Map.of(
                    "valid", false,
                    "reason", "timestamp_outside_skew_window",
                    "skewSeconds", Math.abs(now - ts));
        }

        byte[] body = req.body() == null ? new byte[0] : req.body();
        boolean ok = HmacSigner.verify(
                req.method(), req.path(), body,
                req.timestamp(), req.idempotencyKey(),
                secret, req.signature());

        if (!ok) {
            return reject("hmac_mismatch");
        }
        meterRegistry.counter(METRIC_TOTAL, "result", "valid", "reason", "ok").increment();
        return Map.of("valid", true);
    }

    private Map<String, Object> reject(String reason) {
        recordReject(reason);
        return Map.of("valid", false, "reason", reason);
    }

    private void recordReject(String reason) {
        meterRegistry.counter(METRIC_TOTAL, "result", "invalid", "reason", reason).increment();
    }

    public record VerifyRequest(
            String method,
            String path,
            byte[] body,
            String timestamp,
            String idempotencyKey,
            String signature) {}
}
