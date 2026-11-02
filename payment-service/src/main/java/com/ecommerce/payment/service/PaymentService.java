package com.ecommerce.payment.service;

import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.PaymentTransaction;
import com.ecommerce.payment.event.InventoryReservedEvent;
import com.ecommerce.payment.event.PaymentFailedEvent;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import com.ecommerce.payment.gateway.PaymentGateway;
import com.ecommerce.payment.gateway.PaymentGatewayRequest;
import com.ecommerce.payment.gateway.PaymentGatewayResult;
import com.ecommerce.payment.kafka.PaymentEventProducer;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing payment transaction lifecycle, idempotency guarantees,
 * mock gateway coordination, and choreography event publication.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventProducer paymentEventProducer;

    @Transactional
    public PaymentTransaction processInventoryReserved(InventoryReservedEvent event) {
        // 1. Defensive validation of incoming event
        validateEvent(event);

        Long orderId = event.orderId();
        String correlationId = event.correlationId() != null ? event.correlationId() : event.eventId();

        log.info("Processing payment for orderId={}, amount={}, simulateMode={}, correlationId={}",
                orderId, event.totalAmount(), event.paymentSimulateMode(), correlationId);

        // 2. Application-level Idempotency Check
        Optional<PaymentTransaction> existingTxn = paymentRepository.findByOrderId(orderId);
        if (existingTxn.isPresent()) {
            log.warn("Payment transaction already exists for orderId={}. Skipping duplicate event.", orderId);
            return existingTxn.get();
        }

        // 3. Delegate to PaymentGateway abstraction (deterministic simulation)
        PaymentGatewayRequest gatewayRequest = new PaymentGatewayRequest(
                orderId,
                event.totalAmount(),
                "CARD_DEMO",
                event.paymentSimulateMode(),
                event.customerId(),
                event.customerEmail()
        );
        PaymentGatewayResult gatewayResult = paymentGateway.processPayment(gatewayRequest);

        // 4. Build Entity
        PaymentTransaction txn = PaymentTransaction.builder()
                .orderId(orderId)
                .customerId(event.customerId())
                .amount(event.totalAmount())
                .paymentMethod("CARD_DEMO")
                .paymentSimulateMode(event.paymentSimulateMode() != null ? event.paymentSimulateMode().toUpperCase() : "SUCCESS")
                .status(gatewayResult.status())
                .transactionReference(gatewayResult.transactionReference())
                .failureReason(gatewayResult.failureReason())
                .demoNotice(gatewayResult.demoNotice())
                .build();

        // 5. Persist with Database-level concurrency protection
        PaymentTransaction savedTxn;
        try {
            savedTxn = paymentRepository.save(txn);
            paymentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate payment insert detected for orderId={}. Resolving to existing transaction.", orderId);
            return paymentRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new IllegalStateException("Failed to retrieve existing payment for orderId=" + orderId, e));
        }

        // 6. Publish outcome event within transaction boundary
        if (savedTxn.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment AUTHORIZED for orderId={}, txnRef={}, amount={}",
                    orderId, savedTxn.getTransactionReference(), savedTxn.getAmount());

            PaymentSucceededEvent succeededEvent = new PaymentSucceededEvent(
                    UUID.randomUUID().toString(),
                    correlationId,
                    "PaymentSucceededEvent",
                    orderId,
                    savedTxn.getId(),
                    savedTxn.getTransactionReference(),
                    savedTxn.getAmount(),
                    Instant.now()
            );
            paymentEventProducer.publishPaymentSucceeded(succeededEvent);
        } else if (savedTxn.getStatus() == PaymentStatus.FAILED) {
            log.warn("Payment DECLINED for orderId={}, txnRef={}, reason='{}'",
                    orderId, savedTxn.getTransactionReference(), savedTxn.getFailureReason());

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    UUID.randomUUID().toString(),
                    correlationId,
                    "PaymentFailedEvent",
                    orderId,
                    savedTxn.getFailureReason(),
                    Instant.now()
            );
            paymentEventProducer.publishPaymentFailed(failedEvent);
        } else if (savedTxn.getStatus() == PaymentStatus.PENDING) {
            log.info("Payment PENDING for orderId={}, txnRef={}. Awaiting 3DS confirmation.",
                    orderId, savedTxn.getTransactionReference());
        }

        return savedTxn;
    }

    public Optional<PaymentTransaction> getPaymentByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        return paymentRepository.findByOrderId(orderId);
    }

    private void validateEvent(InventoryReservedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("InventoryReservedEvent cannot be null");
        }
        if (event.orderId() == null || event.orderId() <= 0) {
            throw new IllegalArgumentException("Invalid orderId: " + event.orderId());
        }
        if (event.totalAmount() == null || event.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive: " + event.totalAmount());
        }
    }
}
