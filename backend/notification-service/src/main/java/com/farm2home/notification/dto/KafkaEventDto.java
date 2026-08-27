package com.farm2home.notification.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEventDto {
    private String eventType;
    private UUID customerId;
    // The producing event's own entity id - used purely for idempotency (see
    // NotificationServiceImpl.sendForChannel()/getDedupeEntityId()), never for display or
    // template rendering. paymentId/subscriptionId are each the sole id on their DTO, but
    // DeliveryEvent carries BOTH assignmentId and orderId in the same JSON payload - aliasing
    // "orderId" onto this field too would make binding ambiguous for delivery.events messages
    // specifically, so "orderId" deliberately has its own field below instead, and
    // getDedupeEntityId() falls back to it for OrderEvent (which has no assignmentId).
    @JsonAlias({"paymentId", "assignmentId", "subscriptionId"})
    private UUID sourceEventId;
    // OrderEvent's sole entity id; also present (unused for dedup purposes) on DeliveryEvent,
    // which carries its own orderId alongside assignmentId - see sourceEventId's comment.
    private UUID orderId;
    // Set by every producer at publish time (see OrderEvent/PaymentEvent/DeliveryEvent/
    // SubscriptionEvent/CustomerEvent/OtpEvent, which all share this exact field name). A true
    // Kafka redelivery carries the identical timestamp; a genuinely new occurrence of a
    // recurring event type for the same entity (e.g. a subscription paused, resumed, then paused
    // again - same subscriptionId, same eventType) does not - see getDedupeEntityId() and its
    // use in NotificationServiceImpl, which key on (entityId, occurredAt) together rather than
    // entityId alone for exactly this reason.
    private LocalDateTime occurredAt;
    private String orderNumber;
    // OrderEvent's Java field is "totalAmount", not "amount"; PaymentEvent already matches
    // directly ("amount"), so both need to deserialize onto this one field.
    @JsonAlias("totalAmount")
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

    /** The event's own entity id for idempotency purposes - {@link #sourceEventId} if the source
     *  DTO set one (payment/delivery/subscription events), else {@link #orderId} (order events,
     *  which have no assignmentId/paymentId/subscriptionId). Null for event types with neither
     *  (OTP, CUSTOMER_CREATED) - see NotificationServiceImpl.sendForChannel(), which simply skips
     *  idempotency checking in that case rather than treating null as a real key. */
    public UUID getDedupeEntityId() {
        return sourceEventId != null ? sourceEventId : orderId;
    }
}
