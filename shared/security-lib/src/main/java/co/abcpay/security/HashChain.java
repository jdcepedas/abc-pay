package co.abcpay.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helpers to compute payload hashes and chained record hashes for an
 * append-only audit ledger (Maintain Audit Trail tactic).
 */
public final class HashChain {

    public static final String GENESIS_HASH = "0".repeat(64);

    private HashChain() {}

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HmacSigner.toHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(String s) {
        return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deterministic record hash that binds the record to its position and
     * to the previous record. Any mutation, deletion, insertion, or reorder
     * of historical records breaks the chain and is detectable.
     */
    public static String recordHash(long seq,
                                    String eventId,
                                    String eventType,
                                    String payloadHash,
                                    String prevHash,
                                    String createdAtIso) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(seq).append('|')
          .append(eventId).append('|')
          .append(eventType).append('|')
          .append(payloadHash).append('|')
          .append(prevHash).append('|')
          .append(createdAtIso);
        return sha256Hex(sb.toString());
    }
}
