package com.farm2home.order.domain.entity;

import com.farm2home.order.domain.enums.DeliveryDay;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.ScheduleType;
import com.farm2home.order.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Local read-model for subscription data, populated by Kafka events from subscription-service.
 * Used exclusively by DailyOrderGenerationService to avoid runtime cross-service calls.
 */
@Entity
@Table(name = "subscription_snapshots", schema = "`order`")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "subscriptionId")
public class SubscriptionSnapshot {

    @Id
    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "milk_type", nullable = false, length = 50)
    private MilkType milkType;

    @Column(name = "quantity", nullable = false, precision = 5, scale = 2)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 30)
    private ScheduleType scheduleType;

    @Convert(converter = DeliveryDayListConverter.class)
    @Column(name = "delivery_days", length = 50)
    private List<DeliveryDay> deliveryDays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
