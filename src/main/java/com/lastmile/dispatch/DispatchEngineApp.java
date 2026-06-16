package com.lastmile.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dispatch Engine — Microservice 2
 *
 * Responsibilities:
 *   - Consume ORDER_CREATED events from SQS (published by order-hub)
 *   - Assign a driver to the order via external Driver API
 *   - Handle Driver API failures with Circuit Breaker + Retry + Bulkhead
 *   - Idempotent processing — safe to receive same event twice
 *   - Publish DRIVER_ASSIGNED event to SNS (for alert-engine)
 *
 * Distributed Systems Concepts:
 *   - Circuit Breaker: stops calling failed Driver API, fails fast
 *   - Retry + Exponential Backoff: retries transient failures with increasing delay
 *   - Bulkhead: limits concurrent calls to Driver API (prevents cascade failure)
 *   - Idempotency: messageId dedup prevents double-assignment
 *   - Manual SQS Ack: only delete message after successful processing
 */
@SpringBootApplication
public class DispatchEngineApp {
    public static void main(String[] args) {
        SpringApplication.run(DispatchEngineApp.class, args);
    }
}
