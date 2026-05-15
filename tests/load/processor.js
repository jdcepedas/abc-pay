'use strict';

/**
 * Artillery hooks that mirror the A1-A5 scenarios from the Java harness.
 *
 * The HMAC layout matches `co.abcpay.security.HmacSigner.sign` exactly:
 *   HMAC-SHA256(secret,
 *     METHOD.upper() || \n || PATH || \n || RAW_BODY ||
 *     \n || TIMESTAMP || \n || IDEMPOTENCY_KEY)
 *
 * Each hook mutates the outgoing requestParams so Artillery sends the raw
 * bytes we signed (or, for tampering scenarios, the bytes we want the
 * validator to reject).
 */

const crypto = require('crypto');

const SECRET = process.env.ABCPAY_SHARED_SECRET || 'dev-shared-secret-change-me';
const PATH = '/api/payments';
const METHOD = 'POST';

function sign(method, path, body, timestamp, idemKey, secret) {
  const mac = crypto.createHmac('sha256', secret);
  mac.update(method.toUpperCase(), 'utf8');
  mac.update('\n', 'utf8');
  mac.update(path, 'utf8');
  mac.update('\n', 'utf8');
  mac.update(body); // raw bytes
  mac.update('\n', 'utf8');
  mac.update(String(timestamp), 'utf8');
  mac.update('\n', 'utf8');
  mac.update(idemKey, 'utf8');
  return mac.digest('hex');
}

function uuid() {
  return crypto.randomUUID();
}

function nowSec() {
  return Math.floor(Date.now() / 1000);
}

function randAccount() {
  return 'ACC-' + Math.floor(10000 + Math.random() * 89999);
}

function randAmount() {
  return (Math.random() * 4999 + 1).toFixed(2);
}

function buildPayload() {
  // Keys sorted to match the Java canonicalizer's deterministic JSON output.
  // Matters less here (the validator hashes raw bytes), but keeps demos clean.
  return JSON.stringify({
    amount: randAmount(),
    currency: 'COP',
    destinationAccount: randAccount(),
    reference: 'load-' + uuid(),
    sourceAccount: randAccount(),
  });
}

function applyRequest(requestParams, body, ts, idem, signature, scenario) {
  requestParams.headers = requestParams.headers || {};
  requestParams.headers['Content-Type'] = 'application/json';
  requestParams.headers['X-Signature'] = signature;
  requestParams.headers['X-Timestamp'] = String(ts);
  requestParams.headers['X-Idempotency-Key'] = idem;
  // X-Demo-Scenario is purely a telemetry annotation: the gateway tags its
  // metrics with it so Grafana can attribute each request to the A1..A5
  // scenario that produced it. Real clients would not send this header.
  requestParams.headers['X-Demo-Scenario'] = scenario;
  // Buffer keeps Artillery from re-encoding the body before send.
  requestParams.body = Buffer.isBuffer(body) ? body : Buffer.from(body, 'utf8');
  requestParams.json = undefined;
}

// --- A5: control - sign correctly and send the same bytes -------------------
function signValid(requestParams, _ctx, _ee, next) {
  const body = Buffer.from(buildPayload(), 'utf8');
  const ts = nowSec();
  const idem = uuid();
  const sig = sign(METHOD, PATH, body, ts, idem, SECRET);
  applyRequest(requestParams, body, ts, idem, sig, 'A5_valid');
  return next();
}

// --- A1: flip one bit in the body after signing -----------------------------
function signThenBitFlip(requestParams, _ctx, _ee, next) {
  const body = Buffer.from(buildPayload(), 'utf8');
  const ts = nowSec();
  const idem = uuid();
  const sig = sign(METHOD, PATH, body, ts, idem, SECRET);
  const tampered = Buffer.from(body);
  // Pick a non-structural offset to keep the body parseable.
  const offset = Math.min(8, tampered.length - 1);
  tampered[offset] = tampered[offset] ^ 0x01;
  applyRequest(requestParams, tampered, ts, idem, sig, 'A1_bit_flip');
  return next();
}

// --- A2: change a header that was included in the signature -----------------
function signThenChangeTimestamp(requestParams, _ctx, _ee, next) {
  const body = Buffer.from(buildPayload(), 'utf8');
  const ts = nowSec();
  const idem = uuid();
  const sig = sign(METHOD, PATH, body, ts, idem, SECRET);
  applyRequest(requestParams, body, ts + 1, idem, sig, 'A2_header_tamper');
  return next();
}

// --- A3: apply a valid MAC for body X to body Y -----------------------------
function signOtherBody(requestParams, _ctx, _ee, next) {
  const signedBody = Buffer.from(buildPayload(), 'utf8');
  const sentBody = Buffer.from(buildPayload(), 'utf8');
  const ts = nowSec();
  const idem = uuid();
  const sig = sign(METHOD, PATH, signedBody, ts, idem, SECRET);
  applyRequest(requestParams, sentBody, ts, idem, sig, 'A3_signature_swap');
  return next();
}

// --- A4: replay with a timestamp far outside the allowed window -------------
function signWithStaleTimestamp(requestParams, _ctx, _ee, next) {
  const body = Buffer.from(buildPayload(), 'utf8');
  const ts = nowSec() - 3600; // 1h in the past, well past the 300s skew window
  const idem = uuid();
  const sig = sign(METHOD, PATH, body, ts, idem, SECRET);
  applyRequest(requestParams, body, ts, idem, sig, 'A4_stale_replay');
  return next();
}

module.exports = {
  signValid,
  signThenBitFlip,
  signThenChangeTimestamp,
  signOtherBody,
  signWithStaleTimestamp,
};
