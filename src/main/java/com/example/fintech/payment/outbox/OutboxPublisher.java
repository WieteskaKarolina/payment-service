package com.example.fintech.payment.outbox;

import com.example.fintech.kafka.event.PaymentCreatedEvent;
import com.example.fintech.kafka.producer.PaymentEventProducer;
import com.example.fintech.payment.outbox.entity.OutboxEvent;
import com.example.fintech.payment.outbox.repository.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            PaymentEventProducer paymentEventProducer,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.paymentEventProducer = paymentEventProducer;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishEvents() {
        List<OutboxEvent> events =
                outboxEventRepository
                        .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent outboxEvent) {
        try {
            PaymentCreatedEvent event =
                    objectMapper.readValue(
                            outboxEvent.getPayload(),
                            PaymentCreatedEvent.class
                    );

            paymentEventProducer.publishPaymentCreated(event);

            outboxEvent.markAsPublished();
            outboxEventRepository.save(outboxEvent);

        } catch (Exception e) {
            // Event stays unpublished.
            // Publisher will try again
        }
    }
}