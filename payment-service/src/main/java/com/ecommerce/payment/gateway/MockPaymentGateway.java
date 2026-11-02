package com.ecommerce.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Free, deterministic Mock Payment Gateway for development, automated testing, and interview demos.
 * Eliminates external API dependencies and financial risk by simulating card and UPI authorizations locally.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public PaymentGatewayResult processPayment(PaymentGatewayRequest request) {
        log.info("MockPaymentGateway executing simulated payment: orderId={}, amount={}, method={}, mode={}",
                request.orderId(), request.amount(), request.paymentMethod(), request.simulationMode());

        String mode = request.simulationMode() != null ? request.simulationMode().trim().toUpperCase() : "SUCCESS";

        return switch (mode) {
            case "FAILURE" -> {
                String txnRef = "TXN-DECLINED-" + UUID.randomUUID();
                String reason = "Simulated card decline: insufficient test funds or card issuer decline";
                log.warn("MockPaymentGateway simulated DECLINE for orderId={}, txnRef={}", request.orderId(), txnRef);
                yield PaymentGatewayResult.failure(txnRef, reason);
            }
            case "PENDING" -> {
                String txnRef = "TXN-PENDING-" + UUID.randomUUID();
                String reason = "Simulated async 3D-Secure / OTP challenge pending customer action";
                log.info("MockPaymentGateway simulated PENDING for orderId={}, txnRef={}", request.orderId(), txnRef);
                yield PaymentGatewayResult.pending(txnRef, reason);
            }
            default -> {
                String txnRef = "TXN-AUTH-" + UUID.randomUUID();
                log.info("MockPaymentGateway simulated SUCCESS for orderId={}, txnRef={}", request.orderId(), txnRef);
                yield PaymentGatewayResult.success(txnRef);
            }
        };
    }
}

