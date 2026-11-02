package com.ecommerce.payment.gateway;

import com.ecommerce.payment.entity.PaymentStatus;

/**
 * Result returned by the payment gateway containing authorization status,
 * simulated reference, and explicit demonstration notices.
 */
public record PaymentGatewayResult(
        PaymentStatus status,
        String transactionReference,
        String failureReason,
        String demoNotice
) {
    public static final String DEMO_NOTICE_TEXT = "DEMO/SIMULATED PAYMENT ONLY - No actual banking funds transferred";

    public static PaymentGatewayResult success(String transactionReference) {
        return new PaymentGatewayResult(
                PaymentStatus.SUCCESS,
                transactionReference,
                null,
                DEMO_NOTICE_TEXT
        );
    }

    public static PaymentGatewayResult failure(String transactionReference, String failureReason) {
        return new PaymentGatewayResult(
                PaymentStatus.FAILED,
                transactionReference,
                failureReason,
                DEMO_NOTICE_TEXT
        );
    }

    public static PaymentGatewayResult pending(String transactionReference, String pendingReason) {
        return new PaymentGatewayResult(
                PaymentStatus.PENDING,
                transactionReference,
                pendingReason,
                DEMO_NOTICE_TEXT
        );
    }
}

