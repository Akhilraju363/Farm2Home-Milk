package com.farm2home.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEventDto {
    private String eventType;
    private UUID customerId;
    private String orderNumber;
    private String amount;
    private String partnerName;
    private String expectedTime;
    private String paymentReference;
    private String recipientMobile;
    private String recipientEmail;
    private String customerName;
    private String quantity;
    private String milkType;
    private Map<String, String> extra;
}
