package com.payment.payment_service.shared.kafka;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.payment.payment_service.shared.event.TransferStatusChangedEvent;
import com.payment.payment_service.shared.event.UserCreatedEvent;
import com.payment.payment_service.shared.event.WalletCreditedEvent;
import com.payment.payment_service.shared.event.WalletDebitedEvent;
import com.payment.payment_service.transfer.event.TransferCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.users}")
    private String usersTopic;
    @Value("${kafka.topics.wallet-debits}")
    private String walletDebitsTopic;
    @Value("${kafka.topics.wallet-credits}")
    private String walletCreditsTopic;
    @Value("${kafka.topics.transfer-created}")
    private String transferCreatedTopic;
    @Value("${kafka.topics.transfer-status}")
    private String transferStatusTopic;

    public void publishUserCreated(UserCreatedEvent event) {
        this.sendAndLog(usersTopic, event.userId().toString(), event);
    }

    public void publishWalletDebited(WalletDebitedEvent event) {
        this.sendAndLog(walletDebitsTopic, event.walletId().toString(), event);
    }

    public void publishWalletCredited(WalletCreditedEvent event) {
        this.sendAndLog(walletCreditsTopic, event.walletId().toString(), event);
    }

    public void publishTransferStatusChanged(TransferStatusChangedEvent event) {
        this.sendAndLog(transferStatusTopic, event.transferId().toString(), event);
    }

    public void publishTransferCreated(TransferCreatedEvent event) {
        this.sendAndLog(transferCreatedTopic, event.transferId().toString(), event);
    }

    private void sendAndLog(String topic, String key, Object event) {
        kafkaTemplate.send(Objects.requireNonNull(topic), Objects.requireNonNull(key), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error(
                        "Failed to send event to Kafka. topic={}, key={}, payload={}",
                        topic,
                        key,
                        event,
                        ex
                    );
                } else {
                    log.info(
                        "Event sent to Kafka. topic={}, key={}, partition={}, offset={}",
                        topic,
                        key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                    );
                }
            });
    }
}
