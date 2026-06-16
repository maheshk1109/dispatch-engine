package com.lastmile.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastmile.dispatch.model.OrderCreatedEvent;
import com.lastmile.dispatch.resilience.DriverApiClient;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final DriverApiClient driverApiClient;
    private final IdempotencyStore idempotencyStore;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.dispatch-topic-arn}")
    private String dispatchTopicArn;

    public void processOrder(OrderCreatedEvent event) {
        MDC.put("orderId", event.orderId());
        MDC.put("eventId", event.eventId());

        try {
            if (idempotencyStore.isAlreadyProcessed(event.eventId())) {
                log.info("Duplicate event detected — skipping orderId={}", event.orderId());
                return;
            }

            log.info("Processing ORDER_CREATED event for orderId={}", event.orderId());

            String driverId = driverApiClient.assignDriver(event.orderId(), event.deliveryAddress());
            log.info("Driver assigned: driverId={} for orderId={}", driverId, event.orderId());

            publishDriverAssigned(event.orderId(), event.customerId(), driverId);
            idempotencyStore.markProcessed(event.eventId());

        } finally {
            MDC.clear();
        }
    }

    private void publishDriverAssigned(String orderId, String customerId, String driverId) {
        try {
            record DriverAssignedEvent(String orderId, String customerId,
                                       String driverId, String eventType) {}

            // Serialize to JSON explicitly — SnsTemplate may not serialize records correctly
            String payload = objectMapper.writeValueAsString(
                    new DriverAssignedEvent(orderId, customerId, driverId, "DRIVER_ASSIGNED"));

            snsClient.publish(PublishRequest.builder()
                    .topicArn(dispatchTopicArn)
                    .message(payload)
                    .build());

            log.info("Published DRIVER_ASSIGNED event for orderId={}", orderId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish DRIVER_ASSIGNED: " + e.getMessage(), e);
        }
    }
}
