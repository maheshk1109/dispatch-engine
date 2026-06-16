package com.lastmile.dispatch.model;

/**
 * Event received from order-hub via SQS.
 * eventId used for idempotency dedup.
 */
public record OrderCreatedEvent(
        String eventId,
        String orderId,
        String customerId,
        String deliveryAddress,
        Double amount,
        String createdAt,
        String eventType
) {}
