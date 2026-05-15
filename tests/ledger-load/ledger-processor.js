'use strict';

/**
 * Artillery processor for ledger immutability scenarios B1-B4.
 *
 * Mirrors the Java harness (LedgerSuite + LedgerTamperer):
 *   1. TRUNCATE ledger + reset anchor (direct Postgres)
 *   2. Seed N rows via HTTP POST /api/ledger/append
 *   3. GET /api/ledger/verify — expect valid=true
 *   4. Apply attacker SQL mutation
 *   5. GET /api/ledger/verify — expect valid=false
 *
 * Requires:
 *   - Ledger service reachable at ABCPAY_LEDGER_URL (default http://127.0.0.1:8083)
 *   - Postgres reachable at ABCPAY_DB_URL (same credentials as docker-compose)
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { Client } = require('pg');

const LEDGER_URL = (process.env.ABCPAY_LEDGER_URL || 'http://127.0.0.1:8083').replace(/\/$/, '');
const DB_URL = process.env.ABCPAY_DB_URL || 'postgresql://abcpay:abcpay@127.0.0.1:5432/abcpay';
const SEED_SIZE = parseInt(process.env.ABCPAY_LEDGER_SEED_SIZE || '20', 10);

const REPORT_PATH = path.join(__dirname, '..', '..', 'reports', 'ledger-artillery.json');

function log(msg) {
  console.log(`[ledger-load] ${msg}`);
}

async function withDb(fn) {
  const client = new Client({ connectionString: DB_URL });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

async function truncateLedger() {
  await withDb(async (c) => {
    await c.query('TRUNCATE TABLE ledger.ledger_entry RESTART IDENTITY');
    await c.query(
      "UPDATE ledger.anchor SET last_seq = 0, " +
        "last_record_hash = '0000000000000000000000000000000000000000000000000000000000000000', " +
        "anchor_hmac = '', updated_at_iso = '1970-01-01T00:00:00Z' WHERE id = 1"
    );
  });
}

async function appendEvent(eventId, eventType, payload) {
  const res = await fetch(`${LEDGER_URL}/api/ledger/append`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ eventId, eventType, payload }),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`append failed ${res.status}: ${text}`);
  }
}

async function seedLedger(n) {
  for (let i = 0; i < n; i++) {
    const eventId = crypto.randomUUID();
    await appendEvent(eventId, 'demo.event', { i, note: 'seeded' });
  }
}

async function verifyLedger() {
  const res = await fetch(`${LEDGER_URL}/api/ledger/verify`);
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`verify failed ${res.status}: ${text}`);
  }
  return JSON.parse(text);
}

/** B1: delete the most recent row. */
async function tamperB1() {
  await withDb((c) =>
    c.query(
      'DELETE FROM ledger.ledger_entry WHERE seq = (SELECT MAX(seq) FROM ledger.ledger_entry)'
    )
  );
}

/** B2: alter payload of a middle row without fixing hashes. */
async function tamperB2() {
  await withDb(async (c) => {
    const { rows } = await c.query('SELECT COUNT(*)::bigint AS c FROM ledger.ledger_entry');
    const count = Number(rows[0].c);
    if (count < 2) throw new Error('ledger too small for B2');
    const midOffset = Math.floor(count / 2);
    const midRes = await c.query(
      'SELECT seq FROM ledger.ledger_entry ORDER BY seq ASC LIMIT 1 OFFSET $1',
      [midOffset]
    );
    const mid = midRes.rows[0].seq;
    await c.query('UPDATE ledger.ledger_entry SET payload_json = $1 WHERE seq = $2', [
      '{"tampered":true}',
      mid,
    ]);
  });
}

/** B3: insert a forged row without a valid chain. */
async function tamperB3() {
  await withDb((c) =>
    c.query(
      'INSERT INTO ledger.ledger_entry ' +
        '(event_id, event_type, payload_json, payload_hash, prev_hash, record_hash, created_at_iso) ' +
        'VALUES ($1, $2, $3, $4, $5, $6, $7)',
      [
        'forged-event',
        'forged.event',
        '{"forged":true}',
        'deadbeef'.repeat(8),
        'cafebabe'.repeat(8),
        'abad1dea'.repeat(8),
        '1970-01-01T00:00:00Z',
      ]
    )
  );
}

/** B4: copy second row's payload onto first (breaks chain); matches Java harness SQL. */
async function tamperB4() {
  await withDb(async (c) => {
    const rs = await c.query(
      'SELECT seq FROM ledger.ledger_entry ORDER BY seq ASC LIMIT 2'
    );
    if (rs.rows.length < 2) throw new Error('ledger too small for B4');
    const first = rs.rows[0].seq;
    const second = rs.rows[1].seq;
    await c.query(
      'UPDATE ledger.ledger_entry a SET payload_json = b.payload_json ' +
        'FROM ledger.ledger_entry b WHERE a.seq = $1 AND b.seq = $2',
      [first, second]
    );
  });
}

const results = [];

async function runScenario(id, description, tamperFn) {
  log(`${id}: ${description}`);
  await truncateLedger();
  await seedLedger(SEED_SIZE);

  const pre = await verifyLedger();
  if (!pre.valid) {
    throw new Error(`${id} pre-check expected valid=true, got ${JSON.stringify(pre)}`);
  }

  await tamperFn();

  const post = await verifyLedger();
  const detected = post.valid === false;
  if (!detected) {
    throw new Error(
      `${id} post-check expected valid=false, got ${JSON.stringify(post)} (false negative)`
    );
  }

  results.push({
    id,
    description,
    seedSize: SEED_SIZE,
    preValid: pre.valid,
    postValid: post.valid,
    failureCount: post.count != null ? post.count : null,
    failuresSample: Array.isArray(post.failures) ? post.failures.slice(0, 3) : undefined,
  });
  log(`${id}: PASS (verifier detected tampering)`);
}

function writeReport() {
  const dir = path.dirname(REPORT_PATH);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  const payload = {
    generatedAt: new Date().toISOString(),
    ledgerUrl: LEDGER_URL,
    seedSize: SEED_SIZE,
    scenarios: results,
    allPassed: results.length === 4,
  };
  fs.writeFileSync(REPORT_PATH, JSON.stringify(payload, null, 2), 'utf8');
  log(`Wrote ${REPORT_PATH}`);
}

function wrap(done, fn) {
  fn()
    .then(() => done())
    .catch((err) => done(err));
}

function scenarioB1(_context, _events, done) {
  wrap(done, () => runScenario('B1', 'Delete last row', tamperB1));
}

function scenarioB2(_context, _events, done) {
  wrap(done, () => runScenario('B2', 'Alter field in middle row', tamperB2));
}

function scenarioB3(_context, _events, done) {
  wrap(done, () => runScenario('B3', 'Insert forged row', tamperB3));
}

function scenarioB4(_context, _events, done) {
  wrap(done, async () => {
    await runScenario('B4', 'Reorder / cross-row payload mutation', tamperB4);
    writeReport();
  });
}

module.exports = {
  scenarioB1,
  scenarioB2,
  scenarioB3,
  scenarioB4,
};
