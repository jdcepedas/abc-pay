package co.abcpay.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;

/**
 * Produces deterministic JSON bytes so that two payloads with the same logical
 * content always yield the same byte sequence (and thus the same HMAC).
 * Used by clients before signing. Servers should hash the raw bytes received,
 * not re-canonicalize, to faithfully detect any byte-level tampering.
 */
public final class Canonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private Canonicalizer() {}

    public static byte[] canonicalize(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("canonicalize failed", e);
        }
    }

    public static byte[] canonicalize(Object value) {
        try {
            JsonNode node = MAPPER.valueToTree(value);
            return MAPPER.writeValueAsBytes(node);
        } catch (Exception e) {
            throw new IllegalStateException("canonicalize failed", e);
        }
    }

    public static String canonicalizeToString(Object value) {
        return new String(canonicalize(value), StandardCharsets.UTF_8);
    }
}
