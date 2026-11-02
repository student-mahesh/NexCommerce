package com.ecommerce.notification.kafka;

import com.ecommerce.notification.entity.NotificationType;
import com.ecommerce.notification.event.*;
import com.ecommerce.notification.service.NotificationService;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-created-events", groupId = "notification-service-group")
    public void onOrderCreated(String payload, Acknowledgment ack) {
        log.info("Received message on topic order-created-events");
        try {
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            if (event.orderId() == null) {
                log.warn("Discarding malformed OrderCreatedEvent: missing orderId");
                if (ack != null) ack.acknowledge();
                return;
            }

            notificationService.recordNotification(
                    event.eventId(),
                    event.orderId(),
                    event.customerId(),
                    event.customerEmail(),
                    NotificationType.ORDER_CREATED,
                    "Order #" + event.orderId() + " Placed",
                    "Your order totaling $" + event.totalAmount() + " was placed successfully. Securing warehouse inventory.",
                    event.correlationId()
            );
            if (ack != null) ack.acknowledge();
        } catch (JacksonException e) {
            log.error("Failed to parse OrderCreatedEvent JSON. Discarding poison pill: {}", e.getMessage());
            if (ack != null) ack.acknowledge();
        }
    }

    @KafkaListener(topics = "inventory-reserved-events", groupId = "notification-service-group")
    public void onInventoryReserved(String payload, Acknowledgment ack) {
        log.info("Received message on topic inventory-reserved-events");
        try {
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
            if (event.orderId() == null) {
                log.warn("Discarding malformed InventoryReservedEvent: missing orderId");
                if (ack != null) ack.acknowledge();
                return;
            }

            notificationService.recordNotification(
                    event.eventId(),
                    event.orderId(),
                    event.customerId(),
                    event.customerEmail(),
                    NotificationType.INVENTORY_RESERVED,
                    "Warehouse Stock Reserved for Order #" + event.orderId(),
                    "All items have been verified and reserved. Awaiting payment processing.",
                    event.correlationId()
            );
            if (ack != null) ack.acknowledge();
        } catch (JacksonException e) {
            log.error("Failed to parse InventoryReservedEvent JSON. Discarding poison pill: {}", e.getMessage());
            if (ack != null) ack.acknowledge();
        }
    }

    @KafkaListener(topics = "inventory-rejected-events", groupId = "notification-service-group")
    public void onInventoryRejected(String payload, Acknowledgment ack) {
        log.warn("Received message on topic inventory-rejected-events");
        try {
            InventoryRejectedEvent event = objectMapper.readValue(payload, InventoryRejectedEvent.class);
            if (event.orderId() == null) {
                log.warn("Discarding malformed InventoryRejectedEvent: missing orderId");
                if (ack != null) ack.acknowledge();
                return;
            }

            notificationService.recordNotification(
                    event.eventId(),
                    event.orderId(),
                    null,
                    null,
                    NotificationType.INVENTORY_REJECTED,
                    "Order #" + event.orderId() + " Inventory Insufficient",
                    "Unfortunately, your order could not be fulfilled: " + event.reason(),
                    event.correlationId()
            );
            if (ack != null) ack.acknowledge();
        } catch (JacksonException e) {
            log.error("Failed to parse InventoryRejectedEvent JSON. Discarding poison pill: {}", e.getMessage());
            if (ack != null) ack.acknowledge();
        }
    }

    @KafkaListener(topics = "payment-succeeded-events", groupId = "notification-service-group")
    public void onPaymentSucceeded(String payload, Acknowledgment ack) {
        log.info("Received message on topic payment-succeeded-events");
        try {
            PaymentSucceededEvent event = objectMapper.readValue(payload, PaymentSucceededEvent.class);
            if (event.orderId() == null) {
                log.warn("Discarding malformed PaymentSucceededEvent: missing orderId");
                if (ack != null) ack.acknowledge();
                return;
            }

            notificationService.recordNotification(
                    event.eventId(),
                    event.orderId(),
                    null,
                    null,
                    NotificationType.PAYMENT_SUCCEEDED,
                    "Order #" + event.orderId() + " Confirmed!",
                    "Payment authorized (Ref: " + event.transactionReference() + "). Your order is now confirmed and will be shipped soon!",
                    event.correlationId()
            );
            if (ack != null) ack.acknowledge();
        } catch (JacksonException e) {
            log.error("Failed to parse PaymentSucceededEvent JSON. Discarding poison pill: {}", e.getMessage());
            if (ack != null) ack.acknowledge();
        }
    }

    @KafkaListener(topics = "payment-failed-events", groupId = "notification-service-group")
    public void onPaymentFailed(String payload, Acknowledgment ack) {
        log.warn("Received message on topic payment-failed-events");
        try {
            PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
            if (event.orderId() == null) {
                log.warn("Discarding malformed PaymentFailedEvent: missing orderId");
                if (ack != null) ack.acknowledge();
                return;
            }

            notificationService.recordNotification(
                    event.eventId(),
                    event.orderId(),
                    null,
                    null,
                    NotificationType.PAYMENT_FAILED,
                    "Order #" + event.orderId() + " Payment Failed",
                    "Payment was declined: " + event.reason() + ". Reserved items have been released.",
                    event.correlationId()
            );
            if (ack != null) ack.acknowledge();
        } catch (JacksonException e) {
            log.error("Failed to parse PaymentFailedEvent JSON. Discarding poison pill: {}", e.getMessage());
            if (ack != null) ack.acknowledge();
        }
    }
}
