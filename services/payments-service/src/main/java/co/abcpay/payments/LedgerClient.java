package co.abcpay.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class LedgerClient {

    private final RestClient http;
    private final String ledgerUrl;

    public LedgerClient(RestClient http,
                        @Value("${abcpay.ledger-url}") String ledgerUrl) {
        this.http = http;
        this.ledgerUrl = ledgerUrl;
    }

    public Map<?, ?> append(String eventId, String eventType, Object payload) {
        Map<String, Object> body = Map.of(
                "eventId", eventId,
                "eventType", eventType,
                "payload", payload);
        return http.post()
                .uri(ledgerUrl + "/api/ledger/append")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }
}
