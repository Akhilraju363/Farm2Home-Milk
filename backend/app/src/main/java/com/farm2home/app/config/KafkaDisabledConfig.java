package com.farm2home.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;

/**
 * Keeps this deployment startable with <b>no Kafka broker</b>.
 *
 * <p>Each embedded service ships its own {@code kafka.KafkaConfig} ({@code @EnableKafka}) that
 * defines a <i>custom-named</i> {@code ConcurrentKafkaListenerContainerFactory}
 * (e.g. {@code customerKafkaListenerContainerFactory}) referenced explicitly by
 * {@code @KafkaListener(containerFactory = "...")}. Spring Boot's global
 * {@code spring.kafka.listener.auto-startup=false} property is only applied to Boot's own
 * auto-configured default factory, not to these hand-rolled ones — so without this, the
 * consumer containers start on refresh and spin forever trying to reach {@code localhost:9092}.
 *
 * <p>This {@code BeanPostProcessor} forces {@code autoStartup=false} on every listener-container
 * factory in the context. Consumers are still fully wired (endpoints registered, containers
 * created) — they just never start, so startup never blocks on a broker. Producers
 * ({@code KafkaTemplate}) are lazy and unaffected: a send only happens on a mutating request and
 * its future simply completes exceptionally (logged) with no broker.
 *
 * <p><b>Event functionality unavailable in this deployment</b> (until the in-process event
 * bridge phase): customer's {@code CustomerEventConsumer} (auto-provisioning a customer profile
 * row on {@code CUSTOMER_CREATED} from auth-service) and inventory's
 * {@code InventoryEventProducer} (low-stock / stock-change {@code INVENTORY_UPDATED} events that
 * notification-service would turn into alerts). Synchronous REST paths (including
 * order→inventory stock decrement, once order is embedded) are unaffected.
 */
@Configuration
public class KafkaDisabledConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaDisabledConfig.class);

    @Bean
    static BeanPostProcessor kafkaListenerAutoStartupDisabler() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
                    factory.setAutoStartup(false);
                    log.info("Kafka listener container factory '{}' set to autoStartup=false "
                            + "(no broker in this deployment)", beanName);
                }
                return bean;
            }
        };
    }
}
