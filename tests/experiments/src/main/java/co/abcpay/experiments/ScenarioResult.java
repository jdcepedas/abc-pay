package co.abcpay.experiments;

public record ScenarioResult(
        String suite,
        String id,
        String description,
        long iterations,
        long detected,
        long falseNegatives,
        long falsePositives,
        double avgLatencyMs,
        boolean success) {}
