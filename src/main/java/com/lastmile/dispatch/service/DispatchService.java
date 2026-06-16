package com.lastmile.dispatch.service;

import com.lastmile.dispatch.model.OrderCreatedEvent;
import com.lastmile.dispatch.resilience.DriverApiClient;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Dispatch business logic — assign driver and publish result to SNS
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final DriverApiClient driverApiClient;
    private final IdempotencyStore idempotencyStore;
    private final SnsTemplate snsTemplate;

    @Value("${aws.sns.dispatch-topic-arn}")
    private String dispatchTopicArn;

    public void processOrder(OrderCreatedEvent event) {
        MDC.put("orderId", event.orderId());
        MDC.put("eventId", event.eventId());

        try {
            // Idempotency check — skip if already processed
            if (idempotencyStore.isAlreadyProcessed(event.eventId())) {
                log.info("Duplicate event detected — skipping orderId={}", event.orderId());
                return;
            }

            log.info("Processing ORDER_CREATED event for orderId={}", event.orderId());

            // Call Driver API — wrapped with Circuit Breaker + Retry + Bulkhead
            String driverId = driverApiClient.assignDriver(
                    event.orderId(), event.deliveryAddress());

            log.info("Driver assigned: driverId={} for orderId={}", driverId, event.orderId());

            // Publish DRIVER_ASSIGNED event to SNS → alert-engine receives it
            publishDriverAssigned(event.orderId(), event.customerId(), driverId);

            // Mark as processed AFTER successful publish
            idempotencyStore.markProcessed(event.eventId());

        } finally {
            MDC.clear();
        }
    }

    private void publishDriverAssigned(String orderId, String customerId, String driverId) {
        record DriverAssignedEvent(String orderId, String customerId,
                                    String driverId, String eventType) {}

        snsTemplate.send(dispatchTopicArn,
                MessageBuilder
                        .withPayload(new DriverAssignedEvent(orderId, customerId,
                                driverId, "DRIVER_ASSIGNED"))
                        .setHeader("eventType", "DRIVER_ASSIGNED")
                        .build());

        log.info("Published DRIVER_ASSIGNED event for orderId={}", orderId);
    }
}
