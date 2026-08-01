package com.farm2home.customer.kafka;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.customer.domain.entity.Customer;
import com.farm2home.customer.domain.repository.CustomerRepository;
import com.farm2home.events.customer.CustomerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the local customer profile row when auth-service registers a new user.
 * customer.customers.id intentionally equals the CustomerEvent's customerId (== auth
 * User.id) - every other service's "customerId" field already refers to that same UUID,
 * so this is not an independently-generated key.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventConsumer {

    private final CustomerRepository repository;
    private final AuditLogService auditLogService;

    @KafkaListener(
            topics = "customer.events",
            groupId = "customer-service",
            containerFactory = "customerKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(CustomerEvent event) {
        try {
            if (!"CUSTOMER_CREATED".equals(event.getEventType())) return;

            if (repository.existsByIdAndDeletedFalse(event.getCustomerId())) {
                log.debug("Customer profile already exists for {}", event.getCustomerId());
                return;
            }

            String[] nameParts = splitName(event.getCustomerName());
            String customerCode = String.format("CUST-%06d", repository.nextCustomerCodeSeq());

            Customer customer = Customer.builder()
                    .id(event.getCustomerId())
                    .customerCode(customerCode)
                    .firstName(nameParts[0])
                    .lastName(nameParts[1])
                    .mobile(event.getMobile())
                    .email(event.getEmail())
                    .build();

            repository.save(customer);
            log.info("Created customer profile {} for {}", customerCode, event.getCustomerId());
            auditLogService.record(AuditEntry.builder()
                    .action(AuditAction.CREATE)
                    .entityType("Customer")
                    .entityId(customer.getId().toString())
                    .username(event.getMobile())
                    .details("Customer profile " + customerCode + " created")
                    .build());
        } catch (Exception e) {
            log.error("Failed to process CustomerEvent for {}: {}", event.getCustomerId(), e.getMessage(), e);
        }
    }

    private String[] splitName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return new String[]{"Customer", "Customer"};
        }
        int spaceIdx = fullName.indexOf(' ');
        return spaceIdx < 0
                ? new String[]{fullName, fullName}
                : new String[]{fullName.substring(0, spaceIdx), fullName.substring(spaceIdx + 1)};
    }
}
