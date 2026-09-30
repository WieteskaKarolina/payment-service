package com.example.fintech.kafka.producer;

import com.example.fintech.kafka.event.PaymentCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class PaymentEventProducer {

    private static final String TOPIC = "payments";

    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    public PaymentEventProducer(
            KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCreated(PaymentCreatedEvent event) {
        try {
            kafkaTemplate.send(
                    TOPIC,
                    event.paymentId().toString(),
                    event
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while publishing payment event",
                    e
            );
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Failed to publish payment event",
                    e
            );
        }
    }
}