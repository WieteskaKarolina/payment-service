package com.example.fintech.kafka.producer;

import com.example.fintech.kafka.event.PaymentCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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
        kafkaTemplate.send(
                TOPIC,
                event.paymentId().toString(),
                event
        );
    }
}