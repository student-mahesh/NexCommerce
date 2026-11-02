package com.ecommerce.notification.service;

import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.entity.NotificationLog;
import com.ecommerce.notification.entity.NotificationType;
import com.ecommerce.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    @Transactional
    public Optional<NotificationLog> recordNotification(
            String eventId,
            Long orderId,
            Long customerId,
            String customerEmail,
            NotificationType type,
            String subject,
            String message,
            String correlationId
    ) {
        if (orderId == null) {
            log.warn("Cannot record notification: missing orderId");
            return Optional.empty();
        }

        // Generate stable fallback identity if eventId is missing
        String resolvedEventId = (eventId != null && !eventId.isBlank())
                ? eventId.trim()
                : "order-" + orderId + "-" + type.name();

        // Idempotency check 1: Fast database query
        if (notificationRepository.existsByEventId(resolvedEventId)) {
            log.warn("Notification for eventId={} orderId={} already processed. Skipping duplicate.",
                    resolvedEventId, orderId);
            return Optional.empty();
        }

        // If customerId is not present in this event, attempt to resolve it from previous order notifications
        Long resolvedCustomerId = customerId;
        String resolvedEmail = customerEmail;
        if (resolvedCustomerId == null || resolvedEmail == null) {
            Optional<NotificationLog> priorLog = notificationRepository.findFirstByOrderIdAndCustomerIdIsNotNull(orderId);
            if (priorLog.isPresent()) {
                if (resolvedCustomerId == null) {
                    resolvedCustomerId = priorLog.get().getCustomerId();
                }
                if (resolvedEmail == null) {
                    resolvedEmail = priorLog.get().getRecipientEmail();
                }
            }
        }

        log.info("Recording notification audit [{}]: orderId={}, eventId={}, customerId={}, correlationId='{}'",
                type, orderId, resolvedEventId, resolvedCustomerId, correlationId);

        NotificationLog logEntry = NotificationLog.builder()
                .eventId(resolvedEventId)
                .orderId(orderId)
                .customerId(resolvedCustomerId)
                .recipientEmail(resolvedEmail)
                .type(type)
                .subject(subject)
                .message(message)
                .correlationId(correlationId)
                .status("RECORDED")
                .build();

        try {
            NotificationLog saved = notificationRepository.save(logEntry);
            return Optional.of(saved);
        } catch (DataIntegrityViolationException ex) {
            // Idempotency check 2: Catch race conditions caught by database unique constraint
            log.warn("Duplicate event detected by unique constraint for eventId={}. Skipping duplicate.", resolvedEventId);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByOrderId(Long orderId, Long requestingCustomerId, boolean isAdmin) {
        if (!notificationRepository.existsByOrderId(orderId)) {
            throw new IllegalArgumentException("ORDER_NOT_FOUND");
        }

        List<NotificationLog> logs = notificationRepository.findByOrderIdOrderByCreatedAtDesc(orderId);

        // Authorization check: ensure the requesting customer owns this order (unless admin)
        if (!isAdmin) {
            Long orderCustomerId = logs.stream()
                    .map(NotificationLog::getCustomerId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            if (orderCustomerId != null && !orderCustomerId.equals(requestingCustomerId)) {
                throw new SecurityException("ACCESS_DENIED");
            }
        }

        return logs.stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }
}
