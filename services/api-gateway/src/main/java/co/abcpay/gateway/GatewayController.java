package co.abcpay.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * ASR-SEG-02 entry point.
 * <p>
 * 1) Forwards body + signing headers to the signature-validator.
 * 2) On a positive verdict it routes "tráfico íntegro" to the payments
 *    microservice; otherwise it short-circuits with HTTP 401 so the business
 *    layer never observes a tampered request.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Gateway", description = "Edge entry point that forwards integrity-checked traffic")
public class GatewayController {

    private static final String METRIC_REQUESTS = "abcpay.gateway.requests.total";

    private final RestClient http;
    private final String validatorUrl;
    private final String paymentsUrl;
    private final MeterRegistry meterRegistry;

    public GatewayController(RestClient http,
                             @Value("${abcpay.validator-url}") String validatorUrl,
                             @Value("${abcpay.payments-url}") String paymentsUrl,
                             MeterRegistry meterRegistry) {
        this.http = http;
        this.validatorUrl = validatorUrl;
        this.paymentsUrl = paymentsUrl;
        this.meterRegistry = meterRegistry;
    }

    @Operation(
            summary = "Submit a signed payment",
            description = "Validates the HMAC-SHA256 signature via the signature-validator and, if valid, forwards the request to the payments service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment accepted and processed"),
            @ApiResponse(responseCode = "401", description = "Missing signing headers or integrity check failed"),
            @ApiResponse(responseCode = "502", description = "Downstream payments service unreachable"),
            @ApiResponse(responseCode = "503", description = "Signature validator unavailable")
    })
    @PostMapping(path = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPayment(
            @RequestBody byte[] body,
            @Parameter(description = "Hex-encoded HMAC-SHA256 of method|path|body|timestamp|idempotencyKey", required = true)
            @RequestHeader(name = "X-Signature", required = false) String signature,
            @Parameter(description = "Unix epoch seconds when the request was signed", required = true)
            @RequestHeader(name = "X-Timestamp", required = false) String timestamp,
            @Parameter(description = "Unique idempotency key for safe retries", required = true)
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idemKey,
            @Parameter(description = "Optional demo annotation. Set by Artillery's processor.js to attribute traffic to A1..A5 in the dashboard.", hidden = true)
            @RequestHeader(name = "X-Demo-Scenario", required = false) String demoScenario,
            HttpServletRequest req) {

        String scenario = demoScenario == null || demoScenario.isBlank() ? "production" : demoScenario;

        if (signature == null || timestamp == null || idemKey == null) {
            recordOutcome(scenario, "rejected_missing_headers", "missing_headers");
            return ResponseEntity.status(401)
                    .body(Map.of("error", "missing_signing_headers"));
        }

        Map<String, Object> verifyReq = Map.of(
                "method", req.getMethod(),
                "path", req.getRequestURI(),
                "body", body,
                "timestamp", timestamp,
                "idempotencyKey", idemKey,
                "signature", signature);

        Map<?, ?> verdict;
        try {
            verdict = http.post()
                    .uri(validatorUrl + "/api/validator/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(verifyReq)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            recordOutcome(scenario, "validator_unavailable", "validator_unavailable");
            return ResponseEntity.status(503)
                    .body(Map.of("error", "validator_unavailable", "detail", e.getMessage()));
        }

        if (verdict == null || !Boolean.TRUE.equals(verdict.get("valid"))) {
            String reason = verdict == null ? "no_response" : String.valueOf(verdict.get("reason"));
            recordOutcome(scenario, "rejected_integrity", reason);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "integrity_check_failed",
                    "reason", reason));
        }

        try {
            HttpHeaders fwd = new HttpHeaders();
            fwd.setContentType(MediaType.APPLICATION_JSON);
            fwd.add("X-Signature", signature);
            fwd.add("X-Timestamp", timestamp);
            fwd.add("X-Idempotency-Key", idemKey);

            Object result = http.post()
                    .uri(paymentsUrl + "/api/payments")
                    .headers(h -> h.addAll(fwd))
                    .body(body)
                    .retrieve()
                    .body(Object.class);
            recordOutcome(scenario, "accepted", "ok");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            recordOutcome(scenario, "upstream_error", "upstream_error");
            return ResponseEntity.status(502)
                    .body(Map.of("error", "payments_unavailable", "detail", e.getMessage()));
        }
    }

    private void recordOutcome(String scenario, String outcome, String reason) {
        meterRegistry.counter(METRIC_REQUESTS,
                "outcome", outcome,
                "scenario", scenario,
                "reason", reason).increment();
    }
}
