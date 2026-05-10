package co.abcpay.experiments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReportWriter {

    private final Path outDir;

    public ReportWriter(Path outDir) {
        this.outDir = outDir;
    }

    public void write(List<ScenarioResult> integrity, List<ScenarioResult> ledger) throws IOException {
        Files.createDirectories(outDir);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> json = Map.of(
                "integrity", integrity,
                "ledger", ledger,
                "summary", summary(integrity, ledger));
        Files.writeString(outDir.resolve("results.json"), mapper.writeValueAsString(json));

        StringBuilder csv = new StringBuilder();
        csv.append("suite,id,description,iterations,detected,false_negatives,false_positives,avg_latency_ms,success\n");
        for (ScenarioResult r : integrity) appendCsv(csv, r);
        for (ScenarioResult r : ledger) appendCsv(csv, r);
        Files.writeString(outDir.resolve("results.csv"), csv.toString());

        Files.writeString(outDir.resolve("report.md"), buildMarkdown(integrity, ledger));
    }

    private static void appendCsv(StringBuilder csv, ScenarioResult r) {
        csv.append(r.suite()).append(',')
           .append(r.id()).append(',')
           .append('"').append(r.description().replace("\"", "''")).append('"').append(',')
           .append(r.iterations()).append(',')
           .append(r.detected()).append(',')
           .append(r.falseNegatives()).append(',')
           .append(r.falsePositives()).append(',')
           .append(String.format("%.3f", r.avgLatencyMs())).append(',')
           .append(r.success())
           .append('\n');
    }

    private static Map<String, Object> summary(List<ScenarioResult> integrity, List<ScenarioResult> ledger) {
        long totalFn = integrity.stream().mapToLong(ScenarioResult::falseNegatives).sum()
                + ledger.stream().mapToLong(ScenarioResult::falseNegatives).sum();
        long totalFp = integrity.stream().mapToLong(ScenarioResult::falsePositives).sum();
        boolean allOk = integrity.stream().allMatch(ScenarioResult::success)
                && ledger.stream().allMatch(ScenarioResult::success);
        return Map.of(
                "total_false_negatives", totalFn,
                "total_false_positives", totalFp,
                "all_scenarios_pass", allOk);
    }

    private static String buildMarkdown(List<ScenarioResult> integrity, List<ScenarioResult> ledger) {
        StringBuilder md = new StringBuilder();
        md.append("# ASR-SEG-02 Experiment Report\n\n");
        md.append("Goal: detect 100% of in-scope tampering via Verify Message Integrity ")
          .append("and Maintain Audit Trail tactics.\n\n");

        md.append("## Table A - Message Integrity (Validador de Firmas)\n\n");
        md.append(buildTable(integrity));
        md.append("\n## Table B - Audit Trail (Ledger Inmutable)\n\n");
        md.append(buildTable(ledger));

        long totalFn = integrity.stream().mapToLong(ScenarioResult::falseNegatives).sum()
                + ledger.stream().mapToLong(ScenarioResult::falseNegatives).sum();
        boolean allOk = integrity.stream().allMatch(ScenarioResult::success)
                && ledger.stream().allMatch(ScenarioResult::success);

        md.append("\n## Summary\n\n");
        md.append("- All scenarios pass: ").append(allOk).append('\n');
        md.append("- Total false negatives across tampering scenarios: ").append(totalFn).append('\n');
        md.append("- Detection rate for in-scope tampering: ")
          .append(totalFn == 0 ? "100%" : "less than 100%")
          .append('\n');

        md.append("\n## Out of Scope (caveats)\n");
        md.append("- Compromise of signing key material\n");
        md.append("- Compromise of the validator binary itself\n");
        md.append("- Tampering of the source code or container images at build time\n");
        return md.toString();
    }

    private static String buildTable(List<ScenarioResult> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ID | Description | Iter | Detected | FN | FP | Avg ms | Pass |\n");
        sb.append("|----|-------------|------|----------|----|----|--------|------|\n");
        for (ScenarioResult r : rows) {
            sb.append("| ").append(r.id())
              .append(" | ").append(r.description())
              .append(" | ").append(r.iterations())
              .append(" | ").append(r.detected())
              .append(" | ").append(r.falseNegatives())
              .append(" | ").append(r.falsePositives())
              .append(" | ").append(String.format("%.2f", r.avgLatencyMs()))
              .append(" | ").append(r.success() ? "yes" : "no")
              .append(" |\n");
        }
        return sb.toString();
    }
}
