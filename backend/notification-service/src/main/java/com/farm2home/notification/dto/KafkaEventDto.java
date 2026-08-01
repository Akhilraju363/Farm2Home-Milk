package com.farm2home.notification.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    // DeliveryEvent's Java field is "deliveryPartnerName", not "partnerName".
    @JsonAlias("deliveryPartnerName")
    private String partnerName;
    private String expectedTime;
    private String paymentReference;
    // "recipientMobile"/"recipientEmail" is the name every OTHER producer would need to use
    // to reach a customer; CustomerEvent publishes the more naturally-named "mobile"/"email"
    // instead, so accept either spelling rather than forcing the producer-side naming choice.
    @JsonAlias("mobile")
    private String recipientMobile;
    @JsonAlias("email")
    private String recipientEmail;
    private String customerName;
    private String quantity;
    private String milkType;
    // inventory.events (INVENTORY_UPDATED) - not customer-facing, no customerId involved
    private String itemName;
    private Boolean lowStock;
    // otp.events (OTP)
    private String otp;
    // delivery.events (DELIVERY_DELAYED) - reuses DeliveryEvent's failureReason field name
    private String failureReason;
    private Map<String, String> extra;
}
