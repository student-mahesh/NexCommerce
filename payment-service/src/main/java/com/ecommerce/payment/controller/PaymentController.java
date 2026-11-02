package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.ApiResponse;
import com.ecommerce.payment.entity.PaymentTransaction;
import com.ecommerce.payment.exception.ResourceNotFoundException;
import com.ecommerce.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment transaction and status APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment transaction details by order ID")
    public ResponseEntity<ApiResponse<PaymentTransaction>> getPaymentByOrderId(@PathVariable Long orderId) {
        log.info("Received request for payment transaction of orderId={}", orderId);
        return paymentService.getPaymentByOrderId(orderId)
                .map(txn -> ResponseEntity.ok(ApiResponse.ok("Payment transaction retrieved", txn)))
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found for order id: " + orderId));
    }
}
