package com.ecommerce.payment.service;

import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.PaymentTransaction;
import com.ecommerce.payment.event.InventoryReservedEvent;
import com.ecommerce.payment.event.PaymentFailedEvent;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import com.ecommerce.payment.gateway.MockPaymentGateway;
import com.ecommerce.payment.gateway.PaymentGateway;
import com.ecommerce.payment.gateway.PaymentGatewayResult;
import com.ecommerce.payment.kafka.PaymentEventProducer;
import com.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Spy
    private PaymentGateway paymentGateway = new MockPaymentGateway();

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @InjectMocks
    private PaymentService paymentService;

    private InventoryReservedEvent successEvent;
    private InventoryReservedEvent failureEvent;
    private InventoryReservedEvent pendingEvent;

    @BeforeEach
    void setUp() {
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        successEvent = new InventoryReservedEvent(
                eventId,
                correlationId,
                "InventoryReservedEvent",
                101L,
                1L,
                "customer@test.com",
                new BigDecimal("129.99"),
                "SUCCESS",
                List.of(new InventoryReservedEvent.ReservedItemPayload(1L, 1)),
                Instant.now()
        );

        failureEvent = new InventoryReservedEvent(
                UUID.randomUUID().toString(),
                correlationId,
                "InventoryReservedEvent",
                102L,
                1L,
                "customer@test.com",
                new BigDecimal("499.99"),
                "FAILURE",
                List.of(new InventoryReservedEvent.ReservedItemPayload(2L, 1)),
                Instant.now()
        );

        pendingEvent = new InventoryReservedEvent(
                UUID.randomUUID().toString(),
                correlationId,
                "InventoryReservedEvent",
                103L,
                1L,
                "customer@test.com",
                new BigDecimal("99.99"),
                "PENDING",
                List.of(new InventoryReservedEvent.ReservedItemPayload(3L, 1)),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should authorize payment and publish PaymentSucceededEvent on SUCCESS mode")
    void testProcessInventoryReserved_Success() {
        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction txn = invocation.getArgument(0);
            txn.setId(10L);
            return txn;
        });

        PaymentTransaction result = paymentService.processInventoryReserved(successEvent);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getOrderId()).isEqualTo(101L);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("129.99"));
        assertThat(result.getTransactionReference()).startsWith("TXN-AUTH-");
        assertThat(result.getFailureReason()).isNull();
        assertThat(result.getPaymentMethod()).isEqualTo("CARD_DEMO");

        verify(paymentRepository, times(1)).save(any(PaymentTransaction.class));

        ArgumentCaptor<PaymentSucceededEvent> captor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(paymentEventProducer, times(1)).publishPaymentSucceeded(captor.capture());
        PaymentSucceededEvent published = captor.getValue();
        assertThat(published.orderId()).isEqualTo(101L);
        assertThat(published.paymentId()).isEqualTo(10L);
        assertThat(published.amount()).isEqualByComparingTo(new BigDecimal("129.99"));
        assertThat(published.correlationId()).isEqualTo(successEvent.correlationId());
        verify(paymentEventProducer, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("Should decline payment and publish PaymentFailedEvent on FAILURE mode")
    void testProcessInventoryReserved_Failure() {
        when(paymentRepository.findByOrderId(102L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction txn = invocation.getArgument(0);
            txn.setId(20L);
            return txn;
        });

        PaymentTransaction result = paymentService.processInventoryReserved(failureEvent);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getOrderId()).isEqualTo(102L);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("499.99"));
        assertThat(result.getTransactionReference()).startsWith("TXN-DECLINED-");
        assertThat(result.getFailureReason()).contains("Simulated card decline");

        verify(paymentRepository, times(1)).save(any(PaymentTransaction.class));

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentEventProducer, times(1)).publishPaymentFailed(captor.capture());
        PaymentFailedEvent published = captor.getValue();
        assertThat(published.orderId()).isEqualTo(102L);
        assertThat(published.reason()).contains("Simulated card decline");
        assertThat(published.correlationId()).isEqualTo(failureEvent.correlationId());
        verify(paymentEventProducer, never()).publishPaymentSucceeded(any());
    }

    @Test
    @DisplayName("Should persist PENDING payment without publishing events on PENDING mode")
    void testProcessInventoryReserved_Pending() {
        when(paymentRepository.findByOrderId(103L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction txn = invocation.getArgument(0);
            txn.setId(30L);
            return txn;
        });

        PaymentTransaction result = paymentService.processInventoryReserved(pendingEvent);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getTransactionReference()).startsWith("TXN-PENDING-");
        verify(paymentEventProducer, never()).publishPaymentSucceeded(any());
        verify(paymentEventProducer, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("Should ignore duplicate event when orderId is already processed (Application-level idempotency)")
    void testProcessInventoryReserved_Idempotent_DuplicateIgnored() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(1L)
                .orderId(101L)
                .amount(new BigDecimal("129.99"))
                .status(PaymentStatus.SUCCESS)
                .transactionReference("TXN-AUTH-EXISTING")
                .paymentSimulateMode("SUCCESS")
                .build();

        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.of(existing));

        PaymentTransaction result = paymentService.processInventoryReserved(successEvent);

        assertThat(result).isSameAs(existing);
        verify(paymentRepository, never()).save(any());
        verify(paymentEventProducer, never()).publishPaymentSucceeded(any());
        verify(paymentEventProducer, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("Should handle concurrent duplicate insert race condition via DataIntegrityViolationException")
    void testProcessInventoryReserved_ConcurrentRaceCondition() {
        when(paymentRepository.findByOrderId(101L))
                .thenReturn(Optional.empty()) // First check passes
                .thenReturn(Optional.of(PaymentTransaction.builder()
                        .id(99L)
                        .orderId(101L)
                        .status(PaymentStatus.SUCCESS)
                        .build())); // Second check after collision

        when(paymentRepository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate key uk_payment_order_id"));

        PaymentTransaction result = paymentService.processInventoryReserved(successEvent);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getOrderId()).isEqualTo(101L);
        verify(paymentEventProducer, never()).publishPaymentSucceeded(any());
        verify(paymentEventProducer, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when orderId is null or non-positive")
    void testProcessInventoryReserved_InvalidOrderId() {
        InventoryReservedEvent invalidEvent = new InventoryReservedEvent(
                "evt-1", "corr-1", "InventoryReservedEvent", null, 1L, "cust@test.com",
                new BigDecimal("50.00"), "SUCCESS", List.of(), Instant.now()
        );

        assertThatThrownBy(() -> paymentService.processInventoryReserved(invalidEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid orderId");

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when totalAmount is non-positive")
    void testProcessInventoryReserved_InvalidAmount() {
        InventoryReservedEvent invalidEvent = new InventoryReservedEvent(
                "evt-1", "corr-1", "InventoryReservedEvent", 200L, 1L, "cust@test.com",
                new BigDecimal("-10.00"), "SUCCESS", List.of(), Instant.now()
        );

        assertThatThrownBy(() -> paymentService.processInventoryReserved(invalidEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment amount must be positive");

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    @DisplayName("Should rethrow exception and prevent commit when Kafka publishing fails")
    void testProcessInventoryReserved_KafkaPublishFailure() {
        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction txn = invocation.getArgument(0);
            txn.setId(10L);
            return txn;
        });

        doThrow(new RuntimeException("Kafka broker unreachable"))
                .when(paymentEventProducer).publishPaymentSucceeded(any());

        assertThatThrownBy(() -> paymentService.processInventoryReserved(successEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka broker unreachable");
    }

    @Test
    @DisplayName("Should not publish Kafka event when database save fails")
    void testProcessInventoryReserved_DatabaseFailure() {
        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class)))
                .thenThrow(new RuntimeException("Database connection timeout"));

        assertThatThrownBy(() -> paymentService.processInventoryReserved(successEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection timeout");

        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    @DisplayName("getPaymentByOrderId should return payment when found")
    void testGetPaymentByOrderId_Found() {
        PaymentTransaction txn = PaymentTransaction.builder().id(1L).orderId(101L).build();
        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.of(txn));

        Optional<PaymentTransaction> result = paymentService.getPaymentByOrderId(101L);

        assertThat(result).isPresent().contains(txn);
    }

    @Test
    @DisplayName("getPaymentByOrderId should return empty when not found")
    void testGetPaymentByOrderId_NotFound() {
        when(paymentRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        Optional<PaymentTransaction> result = paymentService.getPaymentByOrderId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPaymentByOrderId should throw IllegalArgumentException for invalid ID")
    void testGetPaymentByOrderId_Invalid() {
        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(-5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order ID must be positive");
    }
}
