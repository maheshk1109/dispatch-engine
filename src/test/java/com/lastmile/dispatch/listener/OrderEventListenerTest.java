package com.lastmile.dispatch.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastmile.dispatch.model.OrderCreatedEvent;
import com.lastmile.dispatch.service.DispatchService;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock DispatchService dispatchService;
    @Mock Acknowledgement acknowledgement;
    @Spy  ObjectMapper objectMapper;

    @InjectMocks OrderEventListener listener;

    @Test
    void validMessage_processedAndAcked() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "EVT-001", "ORD-001", "CUST-001",
                "123 George St", 150.0, "2026-06-04T10:00:00Z", "ORDER_CREATED");

        listener.onOrderCreated(
                objectMapper.writeValueAsString(event),
                "MSG-001",
                acknowledgement);

        verify(dispatchService).processOrder(any(OrderCreatedEvent.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void poisonPill_ackedWithoutProcessing() {
        listener.onOrderCreated("NOT_VALID_JSON", "MSG-002", acknowledgement);

        verify(dispatchService, never()).processOrder(any());
        verify(acknowledgement).acknowledge(); // ack poison pill to remove from queue
    }

    @Test
    void transientError_notAcked() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "EVT-002", "ORD-002", "CUST-001",
                "123 George St", 150.0, "2026-06-04T10:00:00Z", "ORDER_CREATED");

        doThrow(new RuntimeException("DB connection refused"))
                .when(dispatchService).processOrder(any());

        listener.onOrderCreated(
                objectMapper.writeValueAsString(event),
                "MSG-003",
                acknowledgement);

        verify(acknowledgement, never()).acknowledge(); // NOT acked — SQS retries
    }
}
