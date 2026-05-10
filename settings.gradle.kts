rootProject.name = "abc-pay"

include(
    "shared:security-lib",
    "services:api-gateway",
    "services:signature-validator",
    "services:payments-service",
    "services:ledger-service",
    "tests:experiments",
)
