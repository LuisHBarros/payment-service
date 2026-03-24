package com.payment.payment_service.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


@Configuration
public class KafkaTopicsConfig {

    @Value("${kafka.topics.users}")
    private String usersTopic;

    @Value("${kafka.topics.wallets}")
    private String walletsTopic;

    @Value("${kafka.topics.transfers}")
    private String transfersTopic;

    @Bean
    public NewTopic usersTopic() {
        return TopicBuilder.name(usersTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic usersTopicDlt() {
        return TopicBuilder.name(usersTopic + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic walletsTopic() {
        return TopicBuilder.name(walletsTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic walletsTopicDlt() {
        return TopicBuilder.name(walletsTopic + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic transfersTopic() {
        return TopicBuilder.name(transfersTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic transfersTopicDlt() {
        return TopicBuilder.name(transfersTopic + ".DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }
}