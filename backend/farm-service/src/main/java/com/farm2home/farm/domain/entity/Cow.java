package com.farm2home.farm.domain.entity;

import com.farm2home.farm.domain.enums.CowStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cows", schema = "farm")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Cow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tag_number", unique = true, nullable = false, length = 20)
    private String tagNumber;

    @Column(name = "cow_name", length = 100)
    private String cowName;

    @Column(name = "breed", nullable = false, length = 100)
    private String breed;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "farm_id")
    private UUID farmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CowStatus status = CowStatus.ACTIVE;

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
