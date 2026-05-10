# Secret Isolation - Where the HMAC Key Lives

This document explains the deliberate decision to keep the HMAC shared secret
out of the routing tier and the business tier. It is part of how the
**Verify Message Integrity** tactic of ASR-SEG-02 is realized in code.

## TL;DR

Only two components in the system ever read `ABCPAY_SHARED_SECRET`:

1. `signature-validator` - to recompute the HMAC over each inbound request.
2. `ledger-service` - to compute the HMAC over the tamper-evident anchor row.

The `api-gateway` and the `payments-service` never see the secret. They do
not need it to do their jobs, so they are never trusted with it. If either is
compromised, the attacker still cannot forge a valid signature.

## Component view: secret ownership

```mermaid
flowchart LR
    Client[Client or Merchant]

    subgraph Routing [Routing tier - SECRET-FREE]
        Gateway[api-gateway]
    end

    subgraph Crypto [Crypto tier - HOLDS THE SECRET]
        Validator[signature-validator]
        LedgerSvc[ledger-service]
    end

    subgraph Business [Business tier - SECRET-FREE]
        Payments[payments-service]
    end

    subgraph Storage [Persistence]
        TxDb[(Postgres - schema payments)]
        LedgerStore[(Postgres - schema ledger)]
        AnchorStore[(ledger.anchor - HMAC-protected)]
    end

    SecretsStore[Secrets Manager / env var ABCPAY_SHARED_SECRET]

    Client -->|"signed request: body + X-Signature, X-Timestamp, X-Idempotency-Key"| Gateway
    Gateway -->|"forward bytes + headers"| Validator
    Validator -->|"valid / invalid"| Gateway
    Gateway -->|"only on valid - integrity-checked traffic"| Payments
    Payments -->|"persist payment row"| TxDb
    Payments -->|"append audit event"| LedgerSvc
    LedgerSvc --> LedgerStore
    LedgerSvc -->|"update + HMAC sign"| AnchorStore

    SecretsStore -.->|"injected at start"| Validator
    SecretsStore -.->|"injected at start"| LedgerSvc
```

The dashed lines from `Secrets Manager / env var` show the only two places the
secret is provisioned. There is no path from `Gateway` or `Payments` to the
secret store.

## Why this shape

| Property | Effect |
|----------|--------|
| Smaller blast radius | Compromise of the gateway or payments service does not yield the secret. |
| Smaller audit boundary | Only two services need the heavy security review and key rotation drill. |
| Independent rotation | Secret rotation only redeploys validator + ledger; routing tier stays online. |
| Independent scaling | HMAC compute scales with validator pods, decoupled from routing. |
| Pluggable algorithm | Swapping HMAC for JWS or HSM-backed signing changes only the crypto tier. |

The cost is the extra hop `Gateway -> Validator -> Gateway -> Payments`. Our
implementation **fails closed** if the validator is unreachable: the gateway
returns HTTP 503 instead of letting unverified traffic through.

## Request flow with annotations on the secret

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant G as ApiGateway (no secret)
    participant V as Validator (has secret)
    participant P as PaymentsService (no secret)
    participant L as LedgerService (has secret)
    participant A as AnchorRow

    C->>G: POST /api/payments + body + X-Signature, X-Timestamp, X-Idempotency-Key
    G->>V: forward raw bytes + signing headers
    Note over V: Recompute HMAC with secret. Check timestamp skew.
    V-->>G: valid=true (or valid=false + reason)
    alt invalid
        G-->>C: 401 integrity_check_failed
    else valid
        G->>P: forward body + headers
        P->>P: persist Payment row (no secret needed)
        P->>L: append(eventId, eventType, payload)
        L->>L: build chain hashes
        L->>A: update last_seq, last_record_hash, anchor_hmac (signed with secret)
        L-->>P: seq, recordHash, prevHash
        P-->>G: payment + ledger receipt
        G-->>C: 200 OK
    end
```

The sequence makes two important things visible:

- The **secret never crosses the wire** between services. Each service that
  has it only ever performs HMAC operations locally.
- The **anchor row is signed** with the same secret. An attacker who only has
  database write access cannot forge an anchor without the secret, so tail
  deletion and orphan rows are detected by the verifier.

## Mapping to the Iteration 2 (AWS) deployment

The same ownership boundaries hold in the cloud target. Only two task roles
are granted permission to read the secret from AWS Secrets Manager:

```mermaid
flowchart LR
    Secrets[AWS Secrets Manager]

    subgraph CryptoCloud [Crypto tier IAM roles]
        ValidatorRole[validator-task-role]
        LedgerRole[ledger-task-role]
    end

    subgraph OtherCloud [Other tier IAM roles - no GetSecretValue]
        GatewayRole[gateway-task-role]
        PaymentsRole[payments-task-role]
    end

    Secrets -->|"GetSecretValue allowed"| ValidatorRole
    Secrets -->|"GetSecretValue allowed"| LedgerRole
    Secrets -. denied .-> GatewayRole
    Secrets -. denied .-> PaymentsRole
```

This is a textbook application of least privilege at the IAM layer and is
worth calling out in the report as the cloud-native realization of the same
property we already enforce locally via environment variables.

## How to verify the property in the running system

A few quick checks confirm the boundaries:

1. The gateway container does not have `ABCPAY_SHARED_SECRET` in its env:
   ```bash
   docker exec abcpay-api-gateway env | grep -i secret || echo "OK: no secret"
   ```
2. The validator does:
   ```bash
   docker exec abcpay-signature-validator env | grep ABCPAY_SHARED_SECRET
   ```
3. The payments service does not:
   ```bash
   docker exec abcpay-payments-service env | grep -i secret || echo "OK: no secret"
   ```
4. The ledger does:
   ```bash
   docker exec abcpay-ledger-service env | grep ABCPAY_SHARED_SECRET
   ```

If you ever see step 1 or step 3 print the secret, an architectural
boundary has been broken and should be reverted.
