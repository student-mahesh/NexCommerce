package com.ecommerce.order.kafka;

import com.ecommerce.order.event.InventoryRejectedEvent;
import com.ecommerce.order.event.PaymentFailedEvent;
import com.ecommerce.order.event.PaymentSucceededEvent;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for asynchronous saga response events from Kafka topics:
 * - payment-succeeded-events
 * - payment-failed-events
 * - inventory-rejected-events
 * Deserializes JSON payloads via Jackson to remain decoupled from foreign producer classpaths.
 */
@Component
@RequiredArgsConstructor
public class OrderSagaResponseListener {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaResponseListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-succeeded-events", groupId = "order-service-group")
    public void onPaymentSucceeded(String payload) {
        log.info("Received message on topic payment-succeeded-events: length={}",
                payload != null ? payload.length() : 0);

        PaymentSucceededEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentSucceededEvent.class);
        } catch (JacksonException e) {
            log.error("Discarding poison-pill message on payment-succeeded-events: malformed JSON payload: {}", e.getMessage());
            return;
        }

        try {
            orderService.handlePaymentSucceeded(event);
        } catch (IllegalStateException e) {
            log.warn("Safely discarding conflicting/invalid PaymentSucceededEvent for orderId={}: {}",
                    event != null ? event.orderId() : null, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Discarding invalid PaymentSucceededEvent: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing PaymentSucceededEvent for orderId={}: {}. Rethrowing for Kafka retry.",
                    event != null ? event.orderId() : null, e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "payment-failed-events", groupId = "order-service-group")
    public void onPaymentFailed(String payload) {
        log.warn("Received message on topic payment-failed-events: length={}",
                payload != null ? payload.length() : 0);

        PaymentFailedEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        } catch (JacksonException e) {
            log.error("Discarding poison-pill message on payment-failed-events: malformed JSON payload: {}", e.getMessage());
            return;
        }

        try {
            orderService.handlePaymentFailed(event);
        } catch (IllegalStateException e) {
            log.warn("Safely discarding conflicting/invalid PaymentFailedEvent for orderId={}: {}",
                    event != null ? event.orderId() : null, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Discarding invalid PaymentFailedEvent: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing PaymentFailedEvent for orderId={}: {}. Rethrowing for Kafka retry.",
                    event != null ? event.orderId() : null, e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "inventory-rejected-events", groupId = "order-service-group")
    public void onInventoryRejected(String payload) {
        log.warn("Received message on topic inventory-rejected-events: length={}",
                payload != null ? payload.length() : 0);

        InventoryRejectedEvent event;
        try {
            event = objectMapper.readValue(payload, InventoryRejectedEvent.class);
        } catch (JacksonException e) {
            log.error("Discarding poison-pill message on inventory-rejected-events: malformed JSON payload: {}", e.getMessage());
            return;
        }

        try {
            orderService.handleInventoryRejected(event);
        } catch (IllegalStateException e) {
            log.warn("Safely discarding conflicting/invalid InventoryRejectedEvent for orderId={}: {}",
                    event != null ? event.orderId() : null, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Discarding invalid InventoryRejectedEvent: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing InventoryRejectedEvent for orderId={}: {}. Rethrowing for Kafka retry.",
                    event != null ? event.orderId() : null, e.getMessage(), e);
            throw e;
        }
    }
}
