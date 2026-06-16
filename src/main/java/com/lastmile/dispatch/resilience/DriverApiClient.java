package com.lastmile.dispatch.resilience;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * External Driver API Client — wrapped with Resilience4j patterns
 *
 * THREE PATTERNS STACKED:
 *
 * 1. @Bulkhead — limits concurrent calls
 *    maxConcurrentCalls=10 → if 11th call comes in → BulkheadFullException immediately
 *    WHY: prevents one slow dependency from consuming all threads
 *    ANALOGY: ship bulkhead — one compartment flooding doesn't sink the whole ship
 *
 * 2. @Retry — retries on failure
 *    maxAttempts=3, exponential backoff 500ms → 1s → 2s
 *    WHY: transient network blips resolve on retry
 *    IMPORTANT: only retry idempotent operations (GET, PUT — not POST without idempotency key)
 *
 * 3. @CircuitBreaker — stops calling failed service
 *    After 50% failure rate in sliding window of 10 calls → OPEN circuit
 *    OPEN: all calls fail fast with CallNotPermittedException (no network call made)
 *    After waitDurationInOpenState=10s → HALF_OPEN → test with 3 calls
 *    If test succeeds → CLOSED (normal operation resumes)
 *
 * ORDER OF EXECUTION (outermost to innermost):
 *    Bulkhead → Retry → CircuitBreaker → actual HTTP call
 *    If bulkhead full → reject immediately (no retry, no circuit breaker check)
 *    If circuit open → fail fast (no retry, no HTTP call)
 *    If HTTP call fails → retry up to 3 times → if all fail → circuit breaker records failure
 *
 * FALLBACK:
 *    assignDriverFallback() called when circuit is OPEN or all retries exhausted
 *    Returns a default driver assignment — order can still proceed with manual assignment later
 */
@Slf4j
@Component
public class DriverApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${aws.driver-api.url}")
    private String driverApiUrl;

    @Bulkhead(name = "driverApi", fallbackMethod = "assignDriverFallback")
    @Retry(name = "driverApi", fallbackMethod = "assignDriverFallback")
    @CircuitBreaker(name = "driverApi", fallbackMethod = "assignDriverFallback")
    public String assignDriver(String orderId, String deliveryAddress) {
        log.info("Calling Driver API for orderId={}", orderId);

        // Real HTTP call to external driver assignment service
        String url = driverApiUrl + "/assign?orderId=" + orderId;
        return restTemplate.getForObject(url, String.class);
    }

    // Fallback — called when circuit is OPEN, bulkhead full, or retries exhausted
    // Returns a placeholder driver ID — ops team handles manual assignment
    private String assignDriverFallback(String orderId, String deliveryAddress, Exception ex) {
        log.warn("Driver API unavailable for orderId={}, using fallback. Reason: {}",
                orderId, ex.getMessage());
        return "FALLBACK_DRIVER"; // sentinel value — triggers manual assignment workflow
    }
}
