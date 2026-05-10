# Experiment Protocol - ASR-SEG-02

## Goal

Demonstrate that the two ASR-SEG-02 tactics implemented in this repository
detect 100% of the in-scope tampering cases.

- **Verify Message Integrity** -> `signature-validator` rejects any request
  whose body or signed headers were modified after signing, or whose
  timestamp falls outside the configured skew window.
- **Maintain Audit Trail** -> `ledger-service` chain verifier rejects any
  ledger that has been mutated, deleted from, inserted into, or reordered.

## Threat model (in scope)

| Class | Attacker capability |
|-------|----------------------|
| In-flight tampering | Can intercept and modify request bytes or headers between client and gateway. |
| At-rest tampering | Has direct read/write access to the ledger table. |

## Out of scope

- Stealing the HMAC shared secret.
- Compromising the validator binary or the signing client.
- Build-time tampering with images / source.
- Side channels (timing, cache).

## Variables

| Type | Name | Source |
|------|------|--------|
| Independent | scenario id (A1..A5, B1..B4) | harness |
| Independent | iterations per scenario | env var |
| Independent | ledger seed size before mutation | env var |
| Dependent | detected (boolean) | derived from HTTP status / verifier verdict |
| Dependent | false negatives | aggregated per scenario |
| Dependent | false positives | only meaningful for control A5 |
| Dependent | average latency in ms | wall-clock per call |

## Procedure

1. Start the stack:
   ```bash
   docker compose up -d --build
   ```
2. Wait for all services to report `UP` (the harness does this automatically
   for the gateway and ledger).
3. Run the harness:
   ```bash
   ./scripts/run-experiments.sh
   ```
4. Inspect `reports/results.json`, `reports/results.csv`, and
   `reports/report.md`.
5. Acceptance: `summary.total_false_negatives == 0` and
   `summary.all_scenarios_pass == true`.

## How each scenario maps to a measurement

### Integrity suite (HTTP-driven)

For each tampering scenario the harness:
1. Builds a payload and a correctly-formed signature.
2. Mutates either the body, a signed header, the signature target, or the
   timestamp.
3. Sends the request through the gateway.
4. Records detection iff the gateway returns a non-2xx response.

### Ledger suite (DB-driven)

For each tampering scenario the harness:
1. Truncates the ledger table.
2. Appends `ABCPAY_LEDGER_SEED_SIZE` events via the public API.
3. Confirms the verifier reports valid (otherwise the run aborts).
4. Applies the mutation (DELETE / UPDATE / INSERT / payload swap).
5. Calls the verifier again.
6. Records detection iff the verifier reports invalid.

## Reporting artifacts

| File | Purpose |
|------|---------|
| `reports/results.json` | Per-scenario detail plus summary block. |
| `reports/results.csv` | Same data, flat, suitable for slides. |
| `reports/report.md` | Markdown report with Table A and Table B already laid out, ready to embed in a presentation. |
