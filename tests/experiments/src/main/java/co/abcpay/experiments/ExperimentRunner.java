package co.abcpay.experiments;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class ExperimentRunner {

    public static void main(String[] args) throws Exception {
        String gatewayUrl = env("ABCPAY_GATEWAY_URL", "http://localhost:8080");
        String ledgerUrl = env("ABCPAY_LEDGER_URL", "http://localhost:8083");
        String secret = env("ABCPAY_SHARED_SECRET", "dev-shared-secret-change-me");
        String jdbcUrl = env("ABCPAY_JDBC_URL", "jdbc:postgresql://localhost:5432/abcpay");
        String jdbcUser = env("ABCPAY_JDBC_USER", "abcpay");
        String jdbcPass = env("ABCPAY_JDBC_PASSWORD", "abcpay");
        int integrityIters = Integer.parseInt(env("ABCPAY_INTEGRITY_ITERATIONS", "100"));
        int ledgerIters = Integer.parseInt(env("ABCPAY_LEDGER_ITERATIONS", "10"));
        int ledgerSeed = Integer.parseInt(env("ABCPAY_LEDGER_SEED_SIZE", "20"));
        Path reportDir = Path.of(env("ABCPAY_REPORT_DIR", "reports"));

        waitForHealth(gatewayUrl + "/actuator/health", "api-gateway");
        waitForHealth(ledgerUrl + "/actuator/health", "ledger-service");

        SigningClient client = new SigningClient(gatewayUrl, secret);
        IntegritySuite a = new IntegritySuite(client, integrityIters);
        List<ScenarioResult> integrity = a.run();

        LedgerHttpClient ledger = new LedgerHttpClient(ledgerUrl);
        LedgerTamperer tamperer = new LedgerTamperer(jdbcUrl, jdbcUser, jdbcPass);
        LedgerSuite b = new LedgerSuite(ledger, tamperer, ledgerIters, ledgerSeed);
        List<ScenarioResult> ledgerResults = b.run();

        new ReportWriter(reportDir).write(integrity, ledgerResults);

        System.out.println();
        System.out.println("=== Integrity Suite (A1-A5) ===");
        integrity.forEach(r -> System.out.println(format(r)));
        System.out.println("=== Ledger Suite (B1-B4) ===");
        ledgerResults.forEach(r -> System.out.println(format(r)));

        long totalFn = integrity.stream().mapToLong(ScenarioResult::falseNegatives).sum()
                + ledgerResults.stream().mapToLong(ScenarioResult::falseNegatives).sum();
        boolean allOk = integrity.stream().allMatch(ScenarioResult::success)
                && ledgerResults.stream().allMatch(ScenarioResult::success);

        System.out.println();
        System.out.println("Total false negatives: " + totalFn);
        System.out.println("All scenarios pass: " + allOk);
        System.out.println("Reports written to: " + reportDir.toAbsolutePath());

        if (!allOk) {
            System.exit(1);
        }
    }

    private static String format(ScenarioResult r) {
        return String.format(
                "[%s] %s - iter=%d detected=%d FN=%d FP=%d avg=%.2fms pass=%s",
                r.id(), r.description(), r.iterations(),
                r.detected(), r.falseNegatives(), r.falsePositives(),
                r.avgLatencyMs(), r.success());
    }

    private static void waitForHealth(String url, String name) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> resp = http.send(
                        HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body().contains("UP")) {
                    return;
                }
            } catch (Exception ignored) {}
            Thread.sleep(1000);
        }
        throw new IllegalStateException("service not healthy: " + name + " at " + url);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
