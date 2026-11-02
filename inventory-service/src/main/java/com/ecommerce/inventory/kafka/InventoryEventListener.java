package com.ecommerce.inventory.kafka;

import com.ecommerce.inventory.event.OrderCreatedEvent;
import com.ecommerce.inventory.event.PaymentFailedEvent;
import com.ecommerce.inventory.event.PaymentSucceededEvent;
import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-created-events", groupId = "inventory-service-group")
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        log.info("Received message on topic order-created-events: key={}", record.key());
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        } catch (JacksonException e) {
            log.error("Discarding unparseable OrderCreatedEvent message (poison pill) on key={}: {}",
                    record.key(), e.getMessage());
            return; // Acknowledge & discard unparseable message to avoid blocking queue
        }

        // Database and business processing exceptions are rethrown so offset is not committed on failure
        try {
            inventoryService.processOrderCreated(event);
        } catch (Exception e) {
            log.error("Failed to process OrderCreatedEvent for orderId={} (will retry / not acknowledge): {}",
                    event.orderId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "payment-failed-events", groupId = "inventory-service-group")
    public void onPaymentFailed(ConsumerRecord<String, String> record) {
        log.warn("Received message on topic payment-failed-events: key={}", record.key());
        PaymentFailedEvent event;
        try {
            event = objectMapper.readValue(record.value(), PaymentFailedEvent.class);
        } catch (JacksonException e) {
            log.error("Discarding unparseable PaymentFailedEvent message on key={}: {}",
                    record.key(), e.getMessage());
            return;
        }

        try {
            inventoryService.processPaymentFailedCompensation(event);
        } catch (Exception e) {
            log.error("Failed to process PaymentFailedEvent for orderId={}: {}",
                    event.orderId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "payment-succeeded-events", groupId = "inventory-service-group")
    public void onPaymentSucceeded(ConsumerRecord<String, String> record) {
        log.info("Received message on topic payment-succeeded-events: key={}", record.key());
        PaymentSucceededEvent event;
        try {
            event = objectMapper.readValue(record.value(), PaymentSucceededEvent.class);
        } catch (JacksonException e) {
            log.error("Discarding unparseable PaymentSucceededEvent message on key={}: {}",
                    record.key(), e.getMessage());
            return;
        }

        try {
            inventoryService.processPaymentSucceeded(event);
        } catch (Exception e) {
            log.error("Failed to process PaymentSucceededEvent for orderId={}: {}",
                    event.orderId(), e.getMessage(), e);
            throw e;
        }
    }
}
