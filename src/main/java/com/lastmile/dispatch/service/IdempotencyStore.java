package com.lastmile.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency Store — prevents duplicate processing of SQS messages
 *
 * WHY NEEDED:
 *   SQS guarantees AT-LEAST-ONCE delivery.
 *   Same message CAN arrive twice (network retry, consumer crash mid-processing).
 *   Without idempotency: driver assigned twice to same order.
 *
 * HOW IT WORKS:
 *   Before processing: check if eventId already processed
 *   After processing: store eventId as processed
 *   Duplicate arrives: skip processing, ack message (remove from queue)
 *
 * PRODUCTION NOTE:
 *   ConcurrentHashMap is in-memory — lost on restart.
 *   In production: use DynamoDB table with eventId as PK + TTL for expiry.
 *   DynamoDB conditional write: putItem with condition_not_exists(eventId)
 *   → atomic check + store in one operation → no race condition
 *
 * KEY: eventId (from order-hub outbox) — NOT SQS messageId
 *   SQS messageId changes on redrive from DLQ.
 *   eventId is stable — same value regardless of how many times redelivered.
 */
@Slf4j
@Component
public class IdempotencyStore {

    private final ConcurrentHashMap<String, Long> processed = new ConcurrentHashMap<>();

    public boolean isAlreadyProcessed(String eventId) {
        return processed.containsKey(eventId);
    }

    public void markProcessed(String eventId) {
        processed.put(eventId, System.currentTimeMillis());
        log.debug("Marked eventId={} as processed", eventId);
    }
}
