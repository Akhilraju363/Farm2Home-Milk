package com.farm2home.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Restores Spring Boot's fully-configured {@code ObjectMapper} as the {@code @Primary} one for
 * Spring MVC request/response (de)serialization across <b>all</b> embedded services.
 *
 * <p>order-service's {@code kafka.KafkaConfig} declares a bare {@code @Bean ObjectMapper
 * kafkaObjectMapper()} for its two consumers to parse raw-String Kafka payloads. Boot's own
 * {@code JacksonAutoConfiguration.jacksonObjectMapper} is {@code @ConditionalOnMissingBean},
 * so the mere presence of that bean makes Boot back off — leaving a bare
 * {@code new ObjectMapper() + JavaTimeModule} as the only {@code ObjectMapper}, which MVC would
 * then use for farm / production / customer / inventory / subscription / order responses,
 * dropping every Boot customisation ({@code FAIL_ON_UNKNOWN_PROPERTIES=false},
 * {@code Jdk8Module}, {@code ParameterNamesModule}, {@code spring.jackson.*}, …).
 *
 * <p>This bean is {@code @Primary} and built exactly the way Boot builds its own, so MVC uses a
 * correct mapper again. {@code kafkaObjectMapper} still exists for order's (currently stopped)
 * consumers; a JSON parse there would use this {@code @Primary} one, which handles
 * {@code JavaTimeModule} equally well.
 */
@Configuration
public class AppJacksonConfig {

    @Bean
    @Primary
    ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.createXmlMapper(false).build();
    }
}
