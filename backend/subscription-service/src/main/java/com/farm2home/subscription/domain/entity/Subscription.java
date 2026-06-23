package com.farm2home.subscription.domain.entity;

import com.farm2home.subscription.domain.enums.DeliveryDay;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.ScheduleType;
import com.farm2home.subscription.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", schema = "subscription")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "pause_start")
    private LocalDate pauseStart;

    @Column(name = "pause_end")
    private LocalDate pauseEnd;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean deleted = false;
}
