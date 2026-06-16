package com.lastmile.dispatch.service;

import com.lastmile.dispatch.model.OrderCreatedEvent;
import com.lastmile.dispatch.resilience.DriverApiClient;
import io.awspring.cloud.sns.core.SnsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock DriverApiClient driverApiClient;
    @Mock IdempotencyStore idempotencyStore;
    @Mock SnsTemplate snsTemplate;

    @InjectMocks DispatchService dispatchService;

    private final OrderCreatedEvent event = new OrderCreatedEvent(
            "EVT-001", "ORD-001", "CUST-001",
            "123 George St", 150.0, "2026-06-04T10:00:00Z", "ORDER_CREATED");

    @Test
    void processOrder_success() {
        ReflectionTestUtils.setField(dispatchService, "dispatchTopicArn",
                "arn:aws:sns:ap-southeast-2:123:dispatch-events");

        when(idempotencyStore.isAlreadyProcessed("EVT-001")).thenReturn(false);
        when(driverApiClient.assignDriver(any(), any())).thenReturn("DRV-005");

        dispatchService.processOrder(event);

        verify(driverApiClient).assignDriver("ORD-001", "123 George St");
        verify(snsTemplate).send(anyString(), any());
        verify(idempotencyStore).markProcessed("EVT-001");
    }

    @Test
    void processOrder_duplicate_skips() {
        when(idempotencyStore.isAlreadyProcessed("EVT-001")).thenReturn(true);

        dispatchService.processOrder(event);

        verify(driverApiClient, never()).assignDriver(any(), any());
        verify(snsTemplate, never()).send(anyString(), any());
        verify(idempotencyStore, never()).markProcessed(any());
    }

    @Test
    void processOrder_fallbackDriver_stillPublishes() {
        ReflectionTestUtils.setField(dispatchService, "dispatchTopicArn",
                "arn:aws:sns:ap-southeast-2:123:dispatch-events");

        when(idempotencyStore.isAlreadyProcessed("EVT-001")).thenReturn(false);
        when(driverApiClient.assignDriver(any(), any())).thenReturn("FALLBACK_DRIVER");

        dispatchService.processOrder(event);

        verify(snsTemplate).send(anyString(), any());
        verify(idempotencyStore).markProcessed("EVT-001");
    }
}
