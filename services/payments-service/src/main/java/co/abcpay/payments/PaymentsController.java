package co.abcpay.payments;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Demo payments microservice. Trusts integrity verdict from the gateway/validator
 * pipeline, which is the security boundary in this experiment.
 * Persists transactional state and writes a corresponding immutable audit event.
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Demo payment processor. Idempotent on X-Idempotency-Key.")
public class PaymentsController {

    private static final String METRIC_PAYMENTS = "abcpay.payments.total";

    private final PaymentRepository repo;
    private final LedgerClient ledger;
    private final MeterRegistry meterRegistry;

    public PaymentsController(PaymentRepository repo, LedgerClient ledger, MeterRegistry meterRegistry) {
        this.repo = repo;
        this.ledger = ledger;
        this.meterRegistry = meterRegistry;
    }

    @Operation(
            summary = "Create a payment",
            description = "Persists the payment transaction and writes an immutable 'payment.approved' event to the ledger. Returns the existing record if X-Idempotency-Key has been used before.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment approved or idempotent replay"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request body")
    })
    @PostMapping
    @Transactional
    public ResponseEntity<?> createPayment(
            @RequestBody PaymentRequest req,
            @Parameter(description = "Idempotency key. Repeated calls return the original record.", required = true)
            @RequestHeader(name = "X-Idempotency-Key") String idemKey) {

        if (req == null || req.amount() == null
                || req.amount().compareTo(BigDecimal.ZERO) <= 0
                || req.currency() == null
                || req.sourceAccount() == null
                || req.destinationAccount() == null) {
            meterRegistry.counter(METRIC_PAYMENTS, "status", "rejected").increment();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid_payment_request"));
        }

        return repo.findByIdempotencyKey(idemKey)
                .<ResponseEntity<?>>map(existing -> {
                    meterRegistry.counter(METRIC_PAYMENTS, "status", "replay").increment();
                    return ResponseEntity.ok(toMap(existing, "idempotent_replay"));
                })
                .orElseGet(() -> {
                    PaymentEntity p = new PaymentEntity();
                    p.setId(UUID.randomUUID().toString());
                    p.setIdempotencyKey(idemKey);
                    p.setAmount(req.amount());
                    p.setCurrency(req.currency());
                    p.setSourceAccount(req.sourceAccount());
                    p.setDestinationAccount(req.destinationAccount());
                    p.setStatus("APPROVED");
                    p.setCreatedAt(Instant.now());
                    repo.save(p);

                    Map<String, Object> auditPayload = Map.of(
                            "paymentId", p.getId(),
                            "idempotencyKey", p.getIdempotencyKey(),
                            "amount", p.getAmount().toPlainString(),
                            "currency", p.getCurrency(),
                            "sourceAccount", p.getSourceAccount(),
                            "destinationAccount", p.getDestinationAccount(),
                            "status", p.getStatus());

                    Map<?, ?> ledgerResp = ledger.append(p.getId(), "payment.approved", auditPayload);
                    meterRegistry.counter(METRIC_PAYMENTS, "status", "approved").increment();
                    return ResponseEntity.ok(Map.of(
                            "payment", toMap(p, "approved"),
                            "ledger", ledgerResp));
                });
    }

    private Map<String, Object> toMap(PaymentEntity p, String result) {
        return Map.of(
                "id", p.getId(),
                "amount", p.getAmount().toPlainString(),
                "currency", p.getCurrency(),
                "sourceAccount", p.getSourceAccount(),
                "destinationAccount", p.getDestinationAccount(),
                "status", p.getStatus(),
                "result", result,
                "createdAt", p.getCreatedAt().toString());
    }
}
