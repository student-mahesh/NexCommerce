package com.ecommerce.payment.kafka;

import com.ecommerce.payment.event.InventoryReservedEvent;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for inventory-reserved-events from Apache Kafka.
 * Deserializes JSON payloads via Jackson to decouple internal package names across microservices.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory-reserved-events", groupId = "payment-service-group")
    public void onInventoryReserved(String payload) {
        log.info("Received raw message on topic inventory-reserved-events: length={}",
                payload != null ? payload.length() : 0);

        InventoryReservedEvent event;
        try {
            event = objectMapper.readValue(payload, InventoryReservedEvent.class);
        } catch (JacksonException e) {
            log.error("Discarding poison-pill message on inventory-reserved-events: malformed JSON payload: {}", e.getMessage());
            // Acknowledge poison pill so consumer group is not stalled forever
            return;
        }

        try {
            paymentService.processInventoryReserved(event);
        } catch (IllegalArgumentException e) {
            log.warn("Discarding invalid InventoryReservedEvent for orderId={}: {}",
                    event != null ? event.orderId() : null, e.getMessage());
            // Invalid business payload is not retryable
        } catch (Exception e) {
            log.error("Error processing InventoryReservedEvent for orderId={}: {}. Rethrowing to trigger Kafka retry.",
                    event != null ? event.orderId() : null, e.getMessage(), e);
            throw e;
        }
    }
}
