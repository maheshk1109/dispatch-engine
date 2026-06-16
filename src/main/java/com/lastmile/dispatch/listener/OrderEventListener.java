package com.lastmile.dispatch.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastmile.dispatch.model.OrderCreatedEvent;
import com.lastmile.dispatch.service.DispatchService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * SQS Consumer — listens on dispatch-orders queue
 *
 * MANUAL ACK:
 *   acknowledgementMode=MANUAL — SQS does NOT auto-delete message.
 *   We only ack AFTER successful processing.
 *   If processing fails → no ack → SQS re-delivers after VisibilityTimeout.
 *   After maxReceiveCount retries → moves to DLQ.
 *
 * ERROR CATEGORIES:
 *   Parse error (bad JSON)      → ack immediately (retrying won't fix it)
 *   Transient error (DB down)   → don't ack → SQS retries
 *   Business error (duplicate)  → ack (already processed, safe to delete)
 *   Circuit open (driver API)   → DispatchService uses fallback, still acks
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @SqsListener(value = "${aws.sqs.dispatch-queue}", acknowledgementMode = "MANUAL")
    public void onOrderCreated(String payload,
                               @Header("id") String messageId,
                               Acknowledgement acknowledgement) {

        log.info("Received message messageId={}", messageId);

        // Step 1: Parse — poison pill check
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderCreatedEvent.class);
        } catch (Exception e) {
            log.error("Poison pill — cannot parse message {}: {}", messageId, e.getMessage());
            acknowledgement.acknowledge(); // ack to remove — retrying won't fix parse error
            return;
        }

        // Step 2: Process — transient errors will NOT ack (SQS retries)
        try {
            dispatchService.processOrder(event);
            acknowledgement.acknowledge(); // only ack on success
            log.info("Successfully processed and acked messageId={}", messageId);

        } catch (Exception e) {
            // Transient error — do NOT ack → SQS re-delivers after VisibilityTimeout
            log.error("Failed to process messageId={}, will retry: {}", messageId, e.getMessage());
        }
    }
}
