package com.farm2home.subscription.kafka;

import com.farm2home.events.subscription.SubscriptionEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Producer-only - subscription-service does not consume from Kafka. Mirrors payment-service's
 * {@code KafkaConfig} exactly (same producer settings: acks=all, 3 retries, no type-info
 * headers so the raw JSON stays clean for order-service's/notification-service's own consumers).
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, SubscriptionEvent> subscriptionProducerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3,
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false,
                // SubscriptionEventProducer.publish() never blocks on the returned future (only
                // attaches a whenComplete callback) - but KafkaProducer.send() itself is not
                // guaranteed non-blocking: with no broker reachable, it blocks the calling
                // request thread for up to max.block.ms (default 60s) waiting for cluster
                // metadata before it will even return that future. That stalls create/pause/
                // resume/cancel well past the gateway's own circuit-breaker timeout, which then
                // returns a client-visible 503 - and the still-running request thread's eventual
                // response write fails against the disconnected client, rolling back the
                // otherwise-successful DB transaction. A short bound here makes "Kafka
                // unreachable" fail fast (whenComplete logs it) instead of stalling the request.
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000
        ));
    }

    @Bean
    public KafkaTemplate<String, SubscriptionEvent> subscriptionKafkaTemplate() {
        return new KafkaTemplate<>(subscriptionProducerFactory());
    }
}
