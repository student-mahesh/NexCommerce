package com.ecommerce.inventory.kafka;

import com.ecommerce.inventory.event.InventoryRejectedEvent;
import com.ecommerce.inventory.event.InventoryReleasedEvent;
import com.ecommerce.inventory.event.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventProducer.class);

    public static final String TOPIC_INVENTORY_RESERVED = "inventory-reserved-events";
    public static final String TOPIC_INVENTORY_REJECTED = "inventory-rejected-events";
    public static final String TOPIC_INVENTORY_RELEASED = "inventory-released-events";

    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendInventoryReserved(InventoryReservedEvent event) {
        String key = String.valueOf(event.orderId());
        log.info("Publishing InventoryReservedEvent to topic {} with key={}: orderId={}, eventId={}",
                TOPIC_INVENTORY_RESERVED, key, event.orderId(), event.eventId());
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TOPIC_INVENTORY_RESERVED, key, event);
            SendResult<String, Object> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Successfully published InventoryReservedEvent for orderId={} to partition={}",
                    event.orderId(), result.getRecordMetadata().partition());
        } catch (Exception e) {
            log.error("Failed to publish InventoryReservedEvent for orderId={}: {}", event.orderId(), e.getMessage(), e);
            throw new RuntimeException("Kafka publishing failed for InventoryReservedEvent, orderId=" + event.orderId(), e);
        }
    }

    public void sendInventoryRejected(InventoryRejectedEvent event) {
        String key = String.valueOf(event.orderId());
        log.info("Publishing InventoryRejectedEvent to topic {} with key={}: orderId={}, reason={}",
                TOPIC_INVENTORY_REJECTED, key, event.orderId(), event.reason());
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TOPIC_INVENTORY_REJECTED, key, event);
            SendResult<String, Object> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Successfully published InventoryRejectedEvent for orderId={} to partition={}",
                    event.orderId(), result.getRecordMetadata().partition());
        } catch (Exception e) {
            log.error("Failed to publish InventoryRejectedEvent for orderId={}: {}", event.orderId(), e.getMessage(), e);
            throw new RuntimeException("Kafka publishing failed for InventoryRejectedEvent, orderId=" + event.orderId(), e);
        }
    }

    public void sendInventoryReleased(InventoryReleasedEvent event) {
        String key = String.valueOf(event.orderId());
        log.info("Publishing InventoryReleasedEvent (Compensation) to topic {} with key={}: orderId={}",
                TOPIC_INVENTORY_RELEASED, key, event.orderId());
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TOPIC_INVENTORY_RELEASED, key, event);
            SendResult<String, Object> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Successfully published InventoryReleasedEvent for orderId={} to partition={}",
                    event.orderId(), result.getRecordMetadata().partition());
        } catch (Exception e) {
            log.error("Failed to publish InventoryReleasedEvent for orderId={}: {}", event.orderId(), e.getMessage(), e);
            throw new RuntimeException("Kafka publishing failed for InventoryReleasedEvent, orderId=" + event.orderId(), e);
        }
    }
}
