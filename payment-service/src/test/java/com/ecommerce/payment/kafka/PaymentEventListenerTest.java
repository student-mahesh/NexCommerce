package com.ecommerce.payment.kafka;

import com.ecommerce.payment.event.InventoryReservedEvent;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private PaymentService paymentService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    private String validPayload;

    @BeforeEach
    void setUp() throws Exception {
        InventoryReservedEvent event = new InventoryReservedEvent(
                "evt-100",
                "corr-100",
                "InventoryReservedEvent",
                501L,
                1L,
                "buyer@test.com",
                new BigDecimal("89.99"),
                "SUCCESS",
                List.of(new InventoryReservedEvent.ReservedItemPayload(1L, 2)),
                Instant.now()
        );
        validPayload = objectMapper.writeValueAsString(event);
    }

    @Test
    @DisplayName("Should parse valid JSON payload and delegate to PaymentService")
    void testOnInventoryReserved_ValidPayload() {
        paymentEventListener.onInventoryReserved(validPayload);

        verify(paymentService, times(1)).processInventoryReserved(any(InventoryReservedEvent.class));
    }

    @Test
    @DisplayName("Should discard poison pill malformed JSON without throwing exception")
    void testOnInventoryReserved_PoisonPill() {
        String malformedJson = "{ \"orderId\": 501, \"totalAmount\": invalid_json_syntax ";

        // Must not throw exception
        paymentEventListener.onInventoryReserved(malformedJson);

        verify(paymentService, never()).processInventoryReserved(any());
    }

    @Test
    @DisplayName("Should discard event that fails validation without throwing exception")
    void testOnInventoryReserved_InvalidEvent() {
        doThrow(new IllegalArgumentException("Invalid orderId"))
                .when(paymentService).processInventoryReserved(any(InventoryReservedEvent.class));

        // Must not rethrow IllegalArgumentException
        paymentEventListener.onInventoryReserved(validPayload);

        verify(paymentService, times(1)).processInventoryReserved(any());
    }

    @Test
    @DisplayName("Should rethrow runtime exceptions to trigger Kafka retry")
    void testOnInventoryReserved_RethrowOnDatabaseFailure() {
        doThrow(new RuntimeException("Database down"))
                .when(paymentService).processInventoryReserved(any(InventoryReservedEvent.class));

        assertThatThrownBy(() -> paymentEventListener.onInventoryReserved(validPayload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database down");
    }
}

