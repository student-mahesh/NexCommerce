package com.ecommerce.payment.gateway;

import java.math.BigDecimal;

/**
 * Encapsulates payment authorization request parameters passed to the payment gateway.
 * Contains purely simulated metadata — never real card credentials or CVVs.
 */
public record PaymentGatewayRequest(
        Long orderId,
        BigDecimal amount,
        String paymentMethod,
        String simulationMode,
        Long customerId,
        String customerEmail
) {
}

