package com.ecommerce.payment.kafka;

import com.ecommerce.payment.event.PaymentFailedEvent;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes payment outcome events to Apache Kafka.
 * Publishes synchronously with an explicit timeout so that publishing failures
 * trigger a database rollback within the @Transactional boundary.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    public static final String TOPIC_PAYMENT_SUCCEEDED = "payment-succeeded-events";
    public static final String TOPIC_PAYMENT_FAILED = "payment-failed-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Publishing PaymentSucceededEvent to topic {}: orderId={}, amount={}, txnRef={}",
                TOPIC_PAYMENT_SUCCEEDED, event.orderId(), event.amount(), event.transactionReference());
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    TOPIC_PAYMENT_SUCCEEDED,
                    String.valueOf(event.orderId()),
                    event
            );
            SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);
            log.info("PaymentSucceededEvent published successfully for orderId={}, offset={}",
                    event.orderId(), result.getRecordMetadata().offset());
        } catch (Exception e) {
            log.error("Failed to publish PaymentSucceededEvent for orderId={} to topic {}: {}",
                    event.orderId(), TOPIC_PAYMENT_SUCCEEDED, e.getMessage(), e);
            throw new RuntimeException("Kafka publishing failure for PaymentSucceededEvent orderId=" + event.orderId(), e);
        }
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        log.warn("Publishing PaymentFailedEvent to topic {}: orderId={}, reason='{}'",
                TOPIC_PAYMENT_FAILED, event.orderId(), event.reason());
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    TOPIC_PAYMENT_FAILED,
                    String.valueOf(event.orderId()),
                    event
            );
            SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);
            log.info("PaymentFailedEvent published successfully for orderId={}, offset={}",
                    event.orderId(), result.getRecordMetadata().offset());
        } catch (Exception e) {
            log.error("Failed to publish PaymentFailedEvent for orderId={} to topic {}: {}",
                    event.orderId(), TOPIC_PAYMENT_FAILED, e.getMessage(), e);
            throw new RuntimeException("Kafka publishing failure for PaymentFailedEvent orderId=" + event.orderId(), e);
        }
    }
}
