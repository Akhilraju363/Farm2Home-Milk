package com.farm2home.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "milk")
@Data
public class MilkPriceProperties {

    private Map<String, BigDecimal> prices = new HashMap<>();

    public BigDecimal getPriceFor(String milkType) {
        return prices.getOrDefault(milkType, new BigDecimal("70.00"));
    }
}
