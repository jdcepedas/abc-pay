package co.abcpay.experiments;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Integrity scenarios A1-A5. For each tampered scenario we expect HTTP != 2xx
 * (the gateway short-circuits before any business processing). For the control
 * scenario we expect HTTP 2xx.
 */
public class IntegritySuite {

    private final SigningClient client;
    private final int iterationsPerScenario;

    public IntegritySuite(SigningClient client, int iterationsPerScenario) {
        this.client = client;
        this.iterationsPerScenario = iterationsPerScenario;
    }

    public List<ScenarioResult> run() throws Exception {
        List<ScenarioResult> out = new ArrayList<>();
        out.add(runScenario("A1", "Flip one bit in JSON body",
                () -> client.sendBitFlipped(randomPayload(), idem()), false));
        out.add(runScenario("A2", "Change header included in MAC (timestamp)",
                () -> client.sendHeaderTampered(randomPayload(), idem()), false));
        out.add(runScenario("A3", "Apply valid MAC to a different body",
                () -> client.sendSignatureSwap(randomPayload(), randomPayload(), idem()), false));
        out.add(runScenario("A4", "Replay with timestamp outside allowed window",
                () -> client.sendStaleReplay(randomPayload(), idem(), 3600), false));
        out.add(runScenario("A5", "Benign valid request (control)",
                () -> client.sendValid(randomPayload(), idem()), true));
        return out;
    }

    @FunctionalInterface
    private interface ScenarioCall {
        HttpResponse<String> exec() throws Exception;
    }

    private ScenarioResult runScenario(String id, String description,
                                       ScenarioCall call,
                                       boolean expectAccept) throws Exception {
        long detected = 0;
        long falseNegatives = 0;
        long falsePositives = 0;
        long totalNs = 0;

        for (int i = 0; i < iterationsPerScenario; i++) {
            long t0 = System.nanoTime();
            HttpResponse<String> resp = call.exec();
            long elapsed = System.nanoTime() - t0;
            totalNs += elapsed;

            boolean accepted = resp.statusCode() >= 200 && resp.statusCode() < 300;

            if (expectAccept) {
                if (accepted) detected++;
                else falsePositives++;
            } else {
                if (!accepted) detected++;
                else falseNegatives++;
            }
        }

        double avgMs = totalNs / 1_000_000.0 / iterationsPerScenario;
        boolean success = expectAccept
                ? (falsePositives == 0 && detected == iterationsPerScenario)
                : (falseNegatives == 0 && detected == iterationsPerScenario);

        return new ScenarioResult("integrity", id, description,
                iterationsPerScenario, detected, falseNegatives, falsePositives, avgMs, success);
    }

    private static Map<String, Object> randomPayload() {
        return Map.of(
                "amount", String.format("%.2f", ThreadLocalRandom.current().nextDouble(1.0, 5000.0)),
                "currency", "COP",
                "sourceAccount", "ACC-" + ThreadLocalRandom.current().nextInt(10000, 99999),
                "destinationAccount", "ACC-" + ThreadLocalRandom.current().nextInt(10000, 99999),
                "reference", "demo-" + UUID.randomUUID());
    }

    private static String idem() {
        return UUID.randomUUID().toString();
    }
}
