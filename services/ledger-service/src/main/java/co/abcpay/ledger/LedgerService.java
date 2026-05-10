package co.abcpay.ledger;

import co.abcpay.security.Canonicalizer;
import co.abcpay.security.HashChain;
import co.abcpay.security.HmacSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class LedgerService {

    private static final int ANCHOR_ID = 1;

    private final LedgerRepository repo;
    private final AnchorRepository anchorRepo;
    private final String secret;

    public LedgerService(LedgerRepository repo,
                         AnchorRepository anchorRepo,
                         @Value("${abcpay.shared-secret}") String secret) {
        this.repo = repo;
        this.anchorRepo = anchorRepo;
        this.secret = secret;
    }

    @Transactional
    public LedgerEntry append(String eventId, String eventType, Object payload) {
        AnchorEntity anchor = anchorRepo.findById(ANCHOR_ID)
                .orElseThrow(() -> new IllegalStateException("anchor row missing"));

        String prevHash = anchor.getLastSeq() == 0
                ? HashChain.GENESIS_HASH
                : anchor.getLastRecordHash();

        byte[] canonicalPayload = Canonicalizer.canonicalize(payload);
        String payloadHash = HashChain.sha256Hex(canonicalPayload);
        String createdAt = Instant.now().toString();

        LedgerEntry e = new LedgerEntry();
        e.setEventId(eventId);
        e.setEventType(eventType);
        e.setPayloadJson(new String(canonicalPayload, StandardCharsets.UTF_8));
        e.setPayloadHash(payloadHash);
        e.setPrevHash(prevHash);
        e.setCreatedAtIso(createdAt);
        LedgerEntry saved = repo.save(e);

        String recordHash = HashChain.recordHash(
                saved.getSeq(), eventId, eventType, payloadHash, prevHash, createdAt);
        saved.setRecordHash(recordHash);
        repo.save(saved);

        String anchorHmac = anchorHmac(saved.getSeq(), recordHash);
        anchor.setLastSeq(saved.getSeq());
        anchor.setLastRecordHash(recordHash);
        anchor.setAnchorHmac(anchorHmac);
        anchor.setUpdatedAtIso(Instant.now().toString());
        anchorRepo.save(anchor);

        return saved;
    }

    /**
     * Recomputes the entire chain from genesis and verifies the head against
     * the tamper-evident anchor row. Catches mutations, deletions (including
     * tail deletion via anchor mismatch), forged inserts, and reorders.
     */
    @Transactional(readOnly = true)
    public VerificationResult verifyChain() {
        List<VerificationFailure> failures = new ArrayList<>();
        long count = 0;
        long expectedSeq = 0;
        String prevHash = HashChain.GENESIS_HASH;
        String lastRecordHash = HashChain.GENESIS_HASH;

        try (var stream = repo.streamAllOrderedBySeq()) {
            var it = stream.iterator();
            while (it.hasNext()) {
                LedgerEntry e = it.next();
                count++;
                expectedSeq++;

                if (!Long.valueOf(expectedSeq).equals(e.getSeq())) {
                    failures.add(new VerificationFailure(
                            e.getSeq(), "seq_gap_or_reorder",
                            "expected seq=" + expectedSeq + " got " + e.getSeq()));
                    expectedSeq = e.getSeq();
                }

                String recomputedPayloadHash = HashChain.sha256Hex(e.getPayloadJson());
                if (!recomputedPayloadHash.equals(e.getPayloadHash())) {
                    failures.add(new VerificationFailure(
                            e.getSeq(), "payload_hash_mismatch",
                            "stored=" + e.getPayloadHash() + " recomputed=" + recomputedPayloadHash));
                }

                if (!prevHash.equals(e.getPrevHash())) {
                    failures.add(new VerificationFailure(
                            e.getSeq(), "prev_hash_break",
                            "expected prev=" + prevHash + " got " + e.getPrevHash()));
                }

                String recomputedRecordHash = HashChain.recordHash(
                        e.getSeq(), e.getEventId(), e.getEventType(),
                        e.getPayloadHash(), e.getPrevHash(), e.getCreatedAtIso());
                if (!recomputedRecordHash.equals(e.getRecordHash())) {
                    failures.add(new VerificationFailure(
                            e.getSeq(), "record_hash_mismatch",
                            "stored=" + e.getRecordHash() + " recomputed=" + recomputedRecordHash));
                }

                prevHash = e.getRecordHash() == null ? "" : e.getRecordHash();
                lastRecordHash = prevHash;
            }
        }

        AnchorEntity anchor = anchorRepo.findById(ANCHOR_ID).orElse(null);
        if (anchor == null) {
            failures.add(new VerificationFailure(null, "anchor_missing", "no anchor row"));
        } else {
            String expectedAnchorHmac = anchorHmac(anchor.getLastSeq(), anchor.getLastRecordHash());
            if (!expectedAnchorHmac.equals(anchor.getAnchorHmac()) && anchor.getLastSeq() != 0) {
                failures.add(new VerificationFailure(
                        anchor.getLastSeq(), "anchor_hmac_invalid",
                        "anchor was tampered with"));
            }
            if (anchor.getLastSeq() != count) {
                failures.add(new VerificationFailure(
                        anchor.getLastSeq(), "head_count_mismatch",
                        "anchor.last_seq=" + anchor.getLastSeq() + " actual rows=" + count));
            }
            String expectedHeadHash = count == 0 ? HashChain.GENESIS_HASH : lastRecordHash;
            if (!anchor.getLastRecordHash().equals(expectedHeadHash)) {
                failures.add(new VerificationFailure(
                        anchor.getLastSeq(), "head_hash_mismatch",
                        "anchor.last_record_hash=" + anchor.getLastRecordHash()
                                + " actual head=" + expectedHeadHash));
            }
        }

        return new VerificationResult(failures.isEmpty(), count, failures);
    }

    private String anchorHmac(long lastSeq, String lastRecordHash) {
        String s = "ANCHOR|" + lastSeq + "|" + lastRecordHash;
        return HmacSigner.sign("ANCHOR", "/anchor",
                s.getBytes(StandardCharsets.UTF_8),
                "0", "anchor", secret);
    }

    public record VerificationFailure(Long seq, String code, String detail) {}

    public record VerificationResult(boolean valid, long count, List<VerificationFailure> failures) {}
}
