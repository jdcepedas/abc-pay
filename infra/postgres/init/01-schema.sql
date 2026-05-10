CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE IF NOT EXISTS payments.payment (
    id                  varchar(64) PRIMARY KEY,
    idempotency_key     varchar(128) NOT NULL UNIQUE,
    amount              numeric(18, 2) NOT NULL,
    currency            varchar(3)  NOT NULL,
    source_account      varchar(64) NOT NULL,
    destination_account varchar(64) NOT NULL,
    status              varchar(16) NOT NULL,
    created_at          timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS ledger.ledger_entry (
    seq             bigserial PRIMARY KEY,
    event_id        varchar(64)  NOT NULL,
    event_type      varchar(64)  NOT NULL,
    payload_json    text         NOT NULL,
    payload_hash    varchar(64)  NOT NULL,
    prev_hash       varchar(64)  NOT NULL,
    record_hash     varchar(64),
    created_at_iso  varchar(32)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ledger_entry_event_id ON ledger.ledger_entry(event_id);

CREATE TABLE IF NOT EXISTS ledger.anchor (
    id              integer     PRIMARY KEY,
    last_seq        bigint      NOT NULL,
    last_record_hash varchar(64) NOT NULL,
    anchor_hmac     varchar(64) NOT NULL,
    updated_at_iso  varchar(32) NOT NULL,
    CONSTRAINT anchor_singleton CHECK (id = 1)
);

INSERT INTO ledger.anchor (id, last_seq, last_record_hash, anchor_hmac, updated_at_iso)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000', '', '1970-01-01T00:00:00Z')
ON CONFLICT (id) DO NOTHING;
