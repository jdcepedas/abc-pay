package co.abcpay.experiments;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Ledger scenarios B1-B4. Each iteration:
 *   1. truncate ledger
 *   2. seed N events
 *   3. confirm verifier reports valid
 *   4. apply mutation
 *   5. confirm verifier reports invalid
 * Detection happens when step 5 returns valid=false.
 */
public class LedgerSuite {

    private final LedgerHttpClient ledger;
    private final LedgerTamperer tamperer;
    private final int iterations;
    private final int seedSize;

    public LedgerSuite(LedgerHttpClient ledger, LedgerTamperer tamperer,
                       int iterations, int seedSize) {
        this.ledger = ledger;
        this.tamperer = tamperer;
        this.iterations = iterations;
        this.seedSize = seedSize;
    }

    public List<ScenarioResult> run() throws Exception {
        List<ScenarioResult> out = new ArrayList<>();
        out.add(runScenario("B1", "Delete last row", tamperer::deleteLastRow));
        out.add(runScenario("B2", "Alter field in middle row", tamperer::alterMiddleRow));
        out.add(runScenario("B3", "Insert forged row without fixing chain", tamperer::insertForgedRow));
        out.add(runScenario("B4", "Reorder rows (swap two payloads)", tamperer::reorderRows));
        return out;
    }

    @FunctionalInterface
    private interface Mutation {
        void apply() throws Exception;
    }

    private ScenarioResult runScenario(String id, String description, Mutation mutation) throws Exception {
        long detected = 0;
        long falseNegatives = 0;
        long totalNs = 0;

        for (int i = 0; i < iterations; i++) {
            tamperer.truncate();
            ledger.appendDummyEvents(seedSize);

            JsonNode preCheck = ledger.verify();
            if (!preCheck.path("valid").asBoolean()) {
                throw new IllegalStateException(
                        "pre-check failed for " + id + ": " + preCheck.toPrettyString());
            }

            mutation.apply();

            long t0 = System.nanoTime();
            JsonNode v = ledger.verify();
            long elapsed = System.nanoTime() - t0;
            totalNs += elapsed;

            if (!v.path("valid").asBoolean()) {
                detected++;
            } else {
                falseNegatives++;
            }
        }

        double avgMs = totalNs / 1_000_000.0 / iterations;
        boolean success = falseNegatives == 0 && detected == iterations;
        return new ScenarioResult("ledger", id, description,
                iterations, detected, falseNegatives, 0L, avgMs, success);
    }
}
