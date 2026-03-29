package com.payment.payment_service.shared.config;

import java.util.Objects;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

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

    @Value("${kafka.topics.deposit-completed}")
    private String depositCompletedTopic;

    @Bean
    public NewTopic usersTopic() {
        return TopicBuilder.name(Objects.requireNonNull(usersTopic))
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic usersTopicDlt() {
        return TopicBuilder.name(Objects.requireNonNull(usersTopic) + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic walletDebitsTopic() {
        return TopicBuilder.name(Objects.requireNonNull(walletDebitsTopic))
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic walletDebitsTopicDlt() {
        return TopicBuilder.name(Objects.requireNonNull(walletDebitsTopic) + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic walletCreditsTopic() {
        return TopicBuilder.name(Objects.requireNonNull(walletCreditsTopic))
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic walletCreditsTopicDlt() {
        return TopicBuilder.name(Objects.requireNonNull(walletCreditsTopic) + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic transferCreatedTopic() {
        return TopicBuilder.name(Objects.requireNonNull(transferCreatedTopic))
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic transferCreatedTopicDlt() {
        return TopicBuilder.name(Objects.requireNonNull(transferCreatedTopic) + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic transferStatusTopic() {
        return TopicBuilder.name(Objects.requireNonNull(transferStatusTopic))
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic transferStatusTopicDlt() {
        return TopicBuilder.name(transferStatusTopic + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }
    @Bean
    public NewTopic depositCompletedTopic() {
        return TopicBuilder.name(Objects.requireNonNull(depositCompletedTopic))
            .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic depositCompletedTopicDlt() {
        return TopicBuilder.name(Objects.requireNonNull(depositCompletedTopic) + ".DLT")
            .partitions(1).replicas(1).build();
    }
}