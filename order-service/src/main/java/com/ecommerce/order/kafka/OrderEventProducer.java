package com.ecommerce.order.kafka;

import com.ecommerce.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    public static final String TOPIC_ORDER_CREATED = "order-created-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CompletableFuture<SendResult<String, Object>> publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent to topic {}: orderId={}, correlationId={}, totalAmount={}",
                TOPIC_ORDER_CREATED, event.orderId(), event.correlationId(), event.totalAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                TOPIC_ORDER_CREATED,
                String.valueOf(event.orderId()),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish OrderCreatedEvent for orderId={}: {}", event.orderId(), ex.getMessage(), ex);
            } else {
                log.info("Successfully published OrderCreatedEvent for orderId={}, offset={}",
                        event.orderId(), result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}
