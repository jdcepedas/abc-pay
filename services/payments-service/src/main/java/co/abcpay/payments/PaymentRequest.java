package co.abcpay.payments;

import java.math.BigDecimal;

public record PaymentRequest(
        BigDecimal amount,
        String currency,
        String sourceAccount,
        String destinationAccount,
        String reference) {}
