package com.farm2home.auth.kafka;

import com.farm2home.events.customer.CustomerEvent;
import com.farm2home.events.otp.OtpEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** Backs @Async on CustomerEventProducer/OtpEventProducer - both are called from the
     *  register()/send-otp request thread, and publishing must never block the HTTP response on
     *  Kafka (which can be unreachable). Small and bounded: this only ever carries two lightweight
     *  publish calls per registration/OTP send, never request-volume traffic. */
    @Bean
    public TaskExecutor kafkaEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kafka-event-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ProducerFactory<String, CustomerEvent> customerProducerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3,
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false,
                // Publishing itself now runs on kafkaEventExecutor (see CustomerEventProducer/
                // OtpEventProducer's @Async), so this no longer needs to protect the HTTP request
                // thread - it just bounds how long a background thread blocks per attempt during
                // a sustained Kafka outage. This factory builds its own explicit config map, so
                // spring.kafka.producer.* in application.yml has no effect here.
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 1500
        ));
    }

    @Bean
    public KafkaTemplate<String, CustomerEvent> customerKafkaTemplate() {
        return new KafkaTemplate<>(customerProducerFactory());
    }

    @Bean
    public ProducerFactory<String, OtpEvent> otpProducerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3,
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 1500
        ));
    }

    @Bean
    public KafkaTemplate<String, OtpEvent> otpKafkaTemplate() {
        return new KafkaTemplate<>(otpProducerFactory());
    }
}
