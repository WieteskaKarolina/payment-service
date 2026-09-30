package com.example.fintech.kafka.consumer;

import com.example.fintech.kafka.event.PaymentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentEventConsumer.class);

    @KafkaListener(
            topics = "payments",
            groupId = "payment-service"
    )
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        log.info(
                "Payment created: paymentId={}, amount={} {}, sourceAccountId={}, destinationAccountId={}",
                event.paymentId(),
                event.amount(),
                event.currency(),
                event.sourceAccountId(),
                event.destinationAccountId()
        );
    }
}