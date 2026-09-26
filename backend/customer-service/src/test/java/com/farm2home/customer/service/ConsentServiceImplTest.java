package com.farm2home.customer.service;

import com.farm2home.customer.domain.entity.ConsentRecord;
import com.farm2home.customer.domain.enums.ConsentPurpose;
import com.farm2home.customer.domain.repository.ConsentRecordRepository;
import com.farm2home.customer.dto.request.ConsentBulkUpdateRequest;
import com.farm2home.customer.dto.request.ConsentUpdateRequest;
import com.farm2home.customer.dto.response.ConsentResponse;
import com.farm2home.customer.service.impl.ConsentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceImplTest {

    @Mock private ConsentRecordRepository repository;
    @InjectMocks private ConsentServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

    private ConsentUpdateRequest request(ConsentPurpose purpose, boolean granted) {
        ConsentUpdateRequest r = new ConsentUpdateRequest();
        r.setPurpose(purpose);
        r.setGranted(granted);
        r.setNoticeVersion("privacy-notice-v1-DRAFT");
        return r;
    }

    @Nested
    @DisplayName("upsert()")
    class Upsert {

        @Test
        @DisplayName("no existing record for the purpose → creates a new one with the request's values")
        void noExistingRecord_createsNew() {
            when(repository.findByCustomerIdAndPurpose(customerId, ConsentPurpose.MARKETING_COMMUNICATIONS))
                    .thenReturn(Optional.empty());
            when(repository.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            ConsentBulkUpdateRequest bulk = new ConsentBulkUpdateRequest();
            bulk.setConsents(List.of(request(ConsentPurpose.MARKETING_COMMUNICATIONS, true)));

            List<ConsentResponse> result = service.upsert(customerId, bulk, "10.0.0.1", "test-agent", "REGISTRATION");

            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(repository).save(captor.capture());
            ConsentRecord saved = captor.getValue();
            assertThat(saved.getCustomerId()).isEqualTo(customerId);
            assertThat(saved.getPurpose()).isEqualTo(ConsentPurpose.MARKETING_COMMUNICATIONS);
            assertThat(saved.isGranted()).isTrue();
            assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
            assertThat(saved.getUserAgent()).isEqualTo("test-agent");
            assertThat(saved.getSource()).isEqualTo("REGISTRATION");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPurpose()).isEqualTo(ConsentPurpose.MARKETING_COMMUNICATIONS);
            assertThat(result.get(0).isGranted()).isTrue();
        }

        @Test
        @DisplayName("existing record for the purpose → updates the same row (upsert, not a duplicate)")
        void existingRecord_updatesInPlace() {
            ConsentRecord existing = ConsentRecord.builder()
                    .id(UUID.randomUUID()).customerId(customerId)
                    .purpose(ConsentPurpose.ANALYTICS_COOKIES).granted(true).build();
            when(repository.findByCustomerIdAndPurpose(customerId, ConsentPurpose.ANALYTICS_COOKIES))
                    .thenReturn(Optional.of(existing));
            when(repository.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            ConsentBulkUpdateRequest bulk = new ConsentBulkUpdateRequest();
            bulk.setConsents(List.of(request(ConsentPurpose.ANALYTICS_COOKIES, false)));

            service.upsert(customerId, bulk, "10.0.0.2", "test-agent", "SETTINGS");

            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
            assertThat(captor.getValue().isGranted()).isFalse();
        }

        @Test
        @DisplayName("multiple purposes in one call → each one upserted independently")
        void multiplePurposes_eachUpsertedIndependently() {
            when(repository.findByCustomerIdAndPurpose(eq(customerId), any())).thenReturn(Optional.empty());
            when(repository.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            ConsentBulkUpdateRequest bulk = new ConsentBulkUpdateRequest();
            bulk.setConsents(List.of(
                    request(ConsentPurpose.ESSENTIAL_SERVICE, true),
                    request(ConsentPurpose.LOCATION_TRACKING, false)));

            List<ConsentResponse> result = service.upsert(customerId, bulk, "10.0.0.3", "test-agent", "REGISTRATION");

            assertThat(result).hasSize(2);
            verify(repository, times(2)).save(any(ConsentRecord.class));
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns every consent record for the customer, mapped to responses")
        void returnsAllForCustomer() {
            ConsentRecord record = ConsentRecord.builder()
                    .id(UUID.randomUUID()).customerId(customerId)
                    .purpose(ConsentPurpose.ESSENTIAL_SERVICE).granted(true)
                    .createdAt(LocalDateTime.now()).build();
            when(repository.findAllByCustomerId(customerId)).thenReturn(List.of(record));

            List<ConsentResponse> result = service.findAll(customerId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPurpose()).isEqualTo(ConsentPurpose.ESSENTIAL_SERVICE);
            assertThat(result.get(0).isGranted()).isTrue();
        }

        @Test
        @DisplayName("no records → empty list, not null/error")
        void noRecords_emptyList() {
            when(repository.findAllByCustomerId(customerId)).thenReturn(List.of());

            assertThat(service.findAll(customerId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("setOne()")
    class SetOne {

        @Test
        @DisplayName("no existing record → creates one, source is always SETTINGS")
        void noExistingRecord_createsWithSettingsSource() {
            when(repository.findByCustomerIdAndPurpose(customerId, ConsentPurpose.MARKETING_COMMUNICATIONS))
                    .thenReturn(Optional.empty());
            when(repository.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            ConsentResponse response = service.setOne(customerId, ConsentPurpose.MARKETING_COMMUNICATIONS, true, "10.0.0.4", "agent");

            ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getSource()).isEqualTo("SETTINGS");
            assertThat(captor.getValue().isGranted()).isTrue();
            assertThat(response.isGranted()).isTrue();
        }

        @Test
        @DisplayName("existing record → withdrawal (granted=false) updates the same row")
        void existingRecord_withdrawalUpdatesInPlace() {
            ConsentRecord existing = ConsentRecord.builder()
                    .id(UUID.randomUUID()).customerId(customerId)
                    .purpose(ConsentPurpose.MARKETING_COMMUNICATIONS).granted(true).build();
            when(repository.findByCustomerIdAndPurpose(customerId, ConsentPurpose.MARKETING_COMMUNICATIONS))
                    .thenReturn(Optional.of(existing));
            when(repository.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            ConsentResponse response = service.setOne(customerId, ConsentPurpose.MARKETING_COMMUNICATIONS, false, "10.0.0.5", "agent");

            assertThat(response.isGranted()).isFalse();
            verify(repository, never()).save(argThat(r -> !r.getId().equals(existing.getId())));
        }
    }
}
