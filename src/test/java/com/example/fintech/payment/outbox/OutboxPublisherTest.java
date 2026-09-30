package com.example.fintech.payment.outbox;

import com.example.fintech.kafka.event.PaymentCreatedEvent;
import com.example.fintech.kafka.producer.PaymentEventProducer;
import com.example.fintech.payment.outbox.entity.OutboxEvent;
import com.example.fintech.payment.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    void shouldPublishUnpublishedEventAndMarkItAsPublished() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        PaymentCreatedEvent event = new PaymentCreatedEvent(
                paymentId,
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                "PAYMENT_CREATED",
                paymentId,
                "json-payload"
        );

        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));

        when(objectMapper.readValue(
                "json-payload",
                PaymentCreatedEvent.class
        )).thenReturn(event);

        outboxPublisher.publishEvents();

        verify(paymentEventProducer).publishPaymentCreated(event);
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    void shouldNotMarkEventAsPublishedWhenPublishingFails() throws Exception {
        UUID paymentId = UUID.randomUUID();

        PaymentCreatedEvent event = new PaymentCreatedEvent(
                paymentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "PLN"
        );

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                "PAYMENT_CREATED",
                paymentId,
                "json-payload"
        );

        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));

        when(objectMapper.readValue(
                "json-payload",
                PaymentCreatedEvent.class
        )).thenReturn(event);

        doThrow(new RuntimeException("Kafka unavailable"))
                .when(paymentEventProducer)
                .publishPaymentCreated(event);

        outboxPublisher.publishEvents();

        verify(paymentEventProducer).publishPaymentCreated(event);
        verify(outboxEventRepository, never()).save(outboxEvent);
    }
}