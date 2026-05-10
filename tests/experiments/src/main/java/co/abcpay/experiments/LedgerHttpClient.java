package co.abcpay.experiments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class LedgerHttpClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public LedgerHttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void appendDummyEvents(int n) throws Exception {
        for (int i = 0; i < n; i++) {
            Map<String, Object> body = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "demo.event",
                    "payload", Map.of("i", i, "note", "seeded"));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/ledger/append"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new IllegalStateException("seed failed: " + resp.statusCode() + " " + resp.body());
            }
        }
    }

    public JsonNode verify() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/ledger/verify"))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("verify failed: " + resp.statusCode());
        }
        return mapper.readTree(resp.body());
    }
}
