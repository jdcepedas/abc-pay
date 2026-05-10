package co.abcpay.ledger;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Append-only API. Note: there is intentionally no PUT/PATCH/DELETE endpoint.
 * The audit trail can only grow forward.
 */
@RestController
@RequestMapping("/api/ledger")
@Tag(name = "Ledger", description = "Append-only, hash-chained audit trail with tamper-evident anchor")
public class LedgerController {

    private static final String METRIC_APPENDS = "abcpay.ledger.appends.total";
    private static final String METRIC_VERIFY = "abcpay.ledger.verify.total";

    private final LedgerService service;
    private final MeterRegistry meterRegistry;

    public LedgerController(LedgerService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.meterRegistry = meterRegistry;
    }

    @Operation(
            summary = "Append an audit event",
            description = "Adds a new event to the immutable ledger. The record is chained to the previous row via SHA-256 and the chain head is updated in the HMAC-signed anchor row.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event appended; returns seq, recordHash, prevHash and payloadHash"),
            @ApiResponse(responseCode = "400", description = "Missing fields in the request body")
    })
    @PostMapping("/append")
    public ResponseEntity<?> append(@RequestBody AppendRequest req) {
        if (req.eventId() == null || req.eventType() == null || req.payload() == null) {
            meterRegistry.counter(METRIC_APPENDS, "outcome", "rejected").increment();
            return ResponseEntity.badRequest().body(Map.of("error", "missing_fields"));
        }
        LedgerEntry e = service.append(req.eventId(), req.eventType(), req.payload());
        meterRegistry.counter(METRIC_APPENDS, "outcome", "ok", "eventType", req.eventType()).increment();
        return ResponseEntity.ok(Map.of(
                "seq", e.getSeq(),
                "recordHash", e.getRecordHash(),
                "prevHash", e.getPrevHash(),
                "payloadHash", e.getPayloadHash(),
                "createdAt", e.getCreatedAtIso()));
    }

    @Operation(
            summary = "Verify the chain",
            description = "Recomputes every record hash from genesis, validates prev_hash continuity, and checks the HMAC-signed anchor row to detect tail deletions and head tampering.")
    @ApiResponse(responseCode = "200", description = "Verification result with the list of failures (empty if intact)")
    @GetMapping("/verify")
    public ResponseEntity<?> verify() {
        LedgerService.VerificationResult result = service.verifyChain();
        meterRegistry.counter(METRIC_VERIFY, "valid", String.valueOf(result.valid())).increment();
        return ResponseEntity.ok(Map.of(
                "valid", result.valid(),
                "count", result.count(),
                "failures", result.failures()));
    }

    public record AppendRequest(String eventId, String eventType, Object payload) {}
}
