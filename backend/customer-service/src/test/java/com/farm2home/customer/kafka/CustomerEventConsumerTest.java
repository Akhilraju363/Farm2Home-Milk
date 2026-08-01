package com.farm2home.customer.kafka;

import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.customer.domain.entity.Customer;
import com.farm2home.customer.domain.repository.CustomerRepository;
import com.farm2home.events.customer.CustomerEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerEventConsumerTest {

    @Mock private CustomerRepository repository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private CustomerEventConsumer consumer;

    private final UUID customerId = UUID.randomUUID();

    private CustomerEvent buildEvent(String eventType, String customerName) {
        return CustomerEvent.builder()
                .eventType(eventType).customerId(customerId).customerName(customerName)
                .mobile("9876543210").email("test@example.com").occurredAt(LocalDateTime.now())
                .build();
    }

    @Nested @DisplayName("consume()")
    class Consume {

        @Test
        @DisplayName("CUSTOMER_CREATED, new customer → creates profile with id = event.customerId")
        void created_newCustomer_creates() {
            when(repository.existsByIdAndDeletedFalse(customerId)).thenReturn(false);
            when(repository.nextCustomerCodeSeq()).thenReturn(1000L);

            consumer.consume(buildEvent("CUSTOMER_CREATED", "Kafka Tester"));

            ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
            verify(repository).save(captor.capture());
            Customer saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(customerId);
            assertThat(saved.getFirstName()).isEqualTo("Kafka");
            assertThat(saved.getLastName()).isEqualTo("Tester");
            assertThat(saved.getCustomerCode()).isEqualTo("CUST-001000");
        }

        @Test
        @DisplayName("single-word name → both firstName and lastName fall back to it")
        void singleWordName_fallsBack() {
            when(repository.existsByIdAndDeletedFalse(customerId)).thenReturn(false);
            when(repository.nextCustomerCodeSeq()).thenReturn(1000L);

            consumer.consume(buildEvent("CUSTOMER_CREATED", "Cher"));

            ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getFirstName()).isEqualTo("Cher");
            assertThat(captor.getValue().getLastName()).isEqualTo("Cher");
        }

        @Test
        @DisplayName("already exists → skipped, not saved again (idempotency)")
        void alreadyExists_skipped() {
            when(repository.existsByIdAndDeletedFalse(customerId)).thenReturn(true);

            consumer.consume(buildEvent("CUSTOMER_CREATED", "Kafka Tester"));

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("non-CUSTOMER_CREATED eventType → ignored")
        void otherEventType_ignored() {
            consumer.consume(buildEvent("CUSTOMER_UPDATED", "Kafka Tester"));

            verify(repository, never()).save(any());
            verify(repository, never()).existsByIdAndDeletedFalse(any());
        }
    }
}
