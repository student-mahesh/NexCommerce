package com.ecommerce.payment.gateway;

/**
 * Clean abstraction decoupling the microservice business logic from specific
 * payment processors (e.g. Mock Gateway for local/testing, Razorpay/Stripe in production).
 */
public interface PaymentGateway {

    /**
     * Executes payment authorization for the given order request.
     *
     * @param request the payment parameters
     * @return outcome containing status, reference, and reason
     */
    PaymentGatewayResult processPayment(PaymentGatewayRequest request);
}

