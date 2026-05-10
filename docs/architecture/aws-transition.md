# AWS Transition Notes - Iteration 2

This document captures the migration plan from the local Docker stack to AWS.
The intent is that the application code, the experiment harness, and the
ASR-SEG-02 acceptance tests all remain unchanged; only the runtime platform
and a handful of ports/URLs change.

## Component mapping

| Local component | AWS target | Notes |
|-----------------|------------|-------|
| `api-gateway` (Spring Boot, port 8080) | Amazon API Gateway (HTTP API) in front of an ECS Fargate (or EKS) service running the same JAR | API Gateway can also run a Lambda authorizer that delegates to the validator service over a VPC link. |
| `signature-validator` | ECS Fargate task, private subnet, exposed via internal ALB | Reachable only from the API Gateway integration / authorizer. |
| `payments-service` | ECS Fargate task, private subnet | Talks to RDS PostgreSQL via private endpoint and to ledger via internal ALB. |
| `ledger-service` | ECS Fargate task, private subnet | Same as above. The ledger DB role is restricted to INSERT/SELECT only. |
| `postgres` | Amazon RDS for PostgreSQL (Multi-AZ) | Two schemas: `payments`, `ledger`. Optional: separate database per service. |
| Local secret env var | AWS Secrets Manager + IAM | `ABCPAY_SHARED_SECRET` becomes a secret pulled at startup or rotated automatically. |
| Local logs | CloudWatch Logs + OpenTelemetry to AWS X-Ray | Trace IDs propagate across gateway -> validator -> payments -> ledger. |
| Test harness | Run from a CodeBuild job, an ECS one-off task, or a developer laptop targeting the public API Gateway endpoint | Same code, just different `ABCPAY_GATEWAY_URL` and DB endpoint. |

## Key cloud-only hardening

| Concern | Local | AWS |
|---------|-------|-----|
| Secret distribution | env var | AWS Secrets Manager + IAM-scoped task roles |
| TLS termination | none | ACM cert at API Gateway / ALB |
| Network isolation | docker network | VPC, private subnets, security groups limiting validator and ledger to known callers |
| Audit log durability | local volume | RDS automated backups + write-once S3 export of the ledger for tamper-evident long-term storage |
| Key rotation | manual | Secrets Manager rotation Lambda + dual-secret support in validator |
| Database access | full DB user | least-privilege IAM-DB auth, distinct roles for `payments-service` and `ledger-service` |

## Compatibility guarantees

These properties of iteration 1 are preserved in iteration 2 so that the
experiment harness keeps working without code changes:

- The gateway endpoint always exposes `POST /api/payments` and accepts the
  same three signing headers (`X-Signature`, `X-Timestamp`,
  `X-Idempotency-Key`).
- The ledger always exposes `POST /api/ledger/append` and `GET /api/ledger/verify`
  with identical request/response shapes.
- The DB schemas (`payments.payment`, `ledger.ledger_entry`) keep the same
  column names so the harness's offline tampering simulation continues to
  work in a non-production environment.

## Next-iteration scope (not in iteration 1)

- Spoofing tactics: Cognito user pool + risk engine wired into the API
  Gateway authorizer chain.
- Anti-Corruption Layer + message broker for graceful degradation against
  external bank/DIAN endpoints.
- Multi-region deployment for the disponibilidad ASR.
