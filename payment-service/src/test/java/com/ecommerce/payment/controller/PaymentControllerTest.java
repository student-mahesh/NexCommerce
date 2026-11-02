package com.ecommerce.payment.controller;

import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.PaymentTransaction;
import com.ecommerce.payment.exception.GlobalExceptionHandler;
import com.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    @DisplayName("GET /api/payments/order/{orderId} - Should return 200 OK with transaction when found")
    void shouldReturnPaymentWhenFound() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(1L)
                .orderId(101L)
                .customerId(1L)
                .amount(new BigDecimal("129.99"))
                .paymentMethod("CARD_DEMO")
                .paymentSimulateMode("SUCCESS")
                .status(PaymentStatus.SUCCESS)
                .transactionReference("TXN-AUTH-12345")
                .failureReason(null)
                .demoNotice("DEMO/SIMULATED PAYMENT ONLY")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(paymentService.getPaymentByOrderId(101L)).thenReturn(Optional.of(txn));

        mockMvc.perform(get("/api/payments/order/101")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment transaction retrieved"))
                .andExpect(jsonPath("$.data.orderId").value(101))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionReference").value("TXN-AUTH-12345"))
                .andExpect(jsonPath("$.data.demoNotice").value("DEMO/SIMULATED PAYMENT ONLY"));
    }

    @Test
    @DisplayName("GET /api/payments/order/{orderId} - Should return 404 Not Found with standard ErrorResponse when transaction does not exist")
    void shouldReturn404WhenNotFound() throws Exception {
        when(paymentService.getPaymentByOrderId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/payments/order/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment transaction not found for order id: 999"))
                .andExpect(jsonPath("$.path").value("/api/payments/order/999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/payments/order/{orderId} - Should return 500 sanitized ErrorResponse on unexpected internal error")
    void shouldReturn500SanitizedOnUnexpectedPaymentError() throws Exception {
        when(paymentService.getPaymentByOrderId(101L))
                .thenThrow(new RuntimeException("SQL timeout querying payments database"));

        mockMvc.perform(get("/api/payments/order/101")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.path").value("/api/payments/order/101"))
                .andExpect(jsonPath("$.success").value(false));
    }
}
