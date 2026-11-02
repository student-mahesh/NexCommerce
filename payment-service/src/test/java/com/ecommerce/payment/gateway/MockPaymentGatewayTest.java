package com.ecommerce.payment.gateway;

import com.ecommerce.payment.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private MockPaymentGateway mockGateway;

    @BeforeEach
    void setUp() {
        mockGateway = new MockPaymentGateway();
    }

    @Test
    @DisplayName("Should return SUCCESS for SUCCESS simulation mode")
    void shouldReturnSuccessForSuccessMode() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                101L,
                new BigDecimal("129.99"),
                "CARD_DEMO",
                "SUCCESS",
                1L,
                "user@example.com"
        );

        PaymentGatewayResult result = mockGateway.processPayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.transactionReference()).startsWith("TXN-AUTH-");
        assertThat(result.failureReason()).isNull();
        assertThat(result.demoNotice()).contains("DEMO/SIMULATED");
    }

    @Test
    @DisplayName("Should return SUCCESS for default/null simulation mode")
    void shouldReturnSuccessForNullMode() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                102L,
                new BigDecimal("49.99"),
                "CARD_DEMO",
                null,
                1L,
                "user@example.com"
        );

        PaymentGatewayResult result = mockGateway.processPayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.transactionReference()).startsWith("TXN-AUTH-");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("Should return FAILED for FAILURE simulation mode")
    void shouldReturnFailedForFailureMode() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                103L,
                new BigDecimal("299.99"),
                "CARD_DEMO",
                "FAILURE",
                2L,
                "user2@example.com"
        );

        PaymentGatewayResult result = mockGateway.processPayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.transactionReference()).startsWith("TXN-DECLINED-");
        assertThat(result.failureReason()).contains("Simulated card decline");
        assertThat(result.demoNotice()).contains("DEMO/SIMULATED");
    }

    @Test
    @DisplayName("Should return PENDING for PENDING simulation mode")
    void shouldReturnPendingForPendingMode() {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                104L,
                new BigDecimal("99.99"),
                "CARD_DEMO",
                "PENDING",
                3L,
                "user3@example.com"
        );

        PaymentGatewayResult result = mockGateway.processPayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.transactionReference()).startsWith("TXN-PENDING-");
        assertThat(result.failureReason()).contains("Simulated async 3D-Secure");
    }
}

