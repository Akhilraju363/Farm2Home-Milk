package com.farm2home.customer.service;

import com.farm2home.common.core.analytics.CustomerGrowthPoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.reports.CustomerReportRow;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.storage.FileStorageService;
import com.farm2home.customer.domain.entity.Customer;
import com.farm2home.customer.domain.enums.CustomerStatus;
import com.farm2home.customer.domain.repository.CustomerRepository;
import com.farm2home.customer.dto.request.UpdateCustomerRequest;
import com.farm2home.customer.dto.response.CustomerResponse;
import com.farm2home.customer.exception.CustomerException;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.mapper.CustomerMapper;
import com.farm2home.customer.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock private CustomerRepository repository;
    @Mock private CustomerMapper mapper;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private CustomerServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

    private Customer buildCustomer() {
        return Customer.builder()
                .id(customerId).customerCode("CUST-001000")
                .firstName("Kafka").lastName("Tester").mobile("9876543210")
                .status(CustomerStatus.ACTIVE).deleted(false).build();
    }

    private CustomerResponse buildResponse() {
        return CustomerResponse.builder().id(customerId).customerCode("CUST-001000")
                .firstName("Kafka").lastName("Tester").mobile("9876543210").status("ACTIVE").build();
    }

    @Nested @DisplayName("findById()")
    class FindById {
        @Test
        @DisplayName("existing customer → returns response")
        void found() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findById(customerId).getId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("missing customer → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(customerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("findAll()")
    class FindAll {
        @Test
        @DisplayName("returns page of customers")
        void happyPath() {
            when(repository.findAllByDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(buildCustomer())));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findAll(org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
        }
    }

    @Nested @DisplayName("update()")
    class Update {
        @Test
        @DisplayName("existing customer → applies changes and saves")
        void happyPath() {
            Customer entity = buildCustomer();
            UpdateCustomerRequest req = new UpdateCustomerRequest();
            req.setEmail("new@example.com");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.update(customerId, req);

            verify(mapper).updateEntityFromRequest(req, entity);
            verify(repository).save(entity);
        }
    }

    @Nested @DisplayName("delete()")
    class Delete {
        @Test
        @DisplayName("existing inactive customer → sets deleted=true")
        void softDeletes() {
            Customer entity = buildCustomer();
            entity.setStatus(CustomerStatus.INACTIVE);
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));

            service.delete(customerId);

            assertThat(entity.isDeleted()).isTrue();
            verify(repository).save(entity);
        }

        @Test
        @DisplayName("missing customer → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(customerId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("active customer → throws CustomerException")
        void active_throws() {
            Customer entity = buildCustomer();
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.delete(customerId))
                    .isInstanceOf(CustomerException.class)
                    .hasMessageContaining("active");
            verify(repository, never()).save(any());
        }
    }

    @Nested @DisplayName("getSummary()")
    class GetSummary {
        @Test
        @DisplayName("returns total customer count from repository")
        void happyPath() {
            when(repository.countByDeletedFalse()).thenReturn(42L);

            CustomerSummaryResponse result = service.getSummary();

            assertThat(result.getTotalCustomers()).isEqualTo(42L);
        }
    }

    @Nested @DisplayName("uploadProfileImage()")
    class UploadProfileImage {
        @Test
        @DisplayName("valid file → stores and sets profileImageUrl")
        void happyPath() {
            Customer entity = buildCustomer();
            MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));
            when(fileStorageService.store(file, "customers")).thenReturn("customers/uuid.jpg");
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.uploadProfileImage(customerId, file);

            assertThat(entity.getProfileImageUrl()).isEqualTo("/uploads/customers/uuid.jpg");
            verify(repository).save(entity);
        }
    }

    @Nested
    @DisplayName("getReport()")
    class GetReport {

        @Test
        @DisplayName("maps the repository page into report rows and status breakdown totals")
        void happyPath() {
            Customer customer = buildCustomer();
            var page = new PageImpl<>(List.of(customer), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(repository.count(any(Specification.class))).thenReturn(1L, 0L, 0L);

            ReportPage<CustomerReportRow, CustomerReportSummary> result = service.getReport(
                    null, null, CustomerStatus.ACTIVE, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCustomerId()).isEqualTo(customerId);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Kafka Tester");
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getSummary().getTotalCustomers()).isEqualTo(1);
            assertThat(result.getSummary().getActiveCustomers()).isEqualTo(1);
            assertThat(result.getSummary().getInactiveCustomers()).isEqualTo(0);
            assertThat(result.getSummary().getSuspendedCustomers()).isEqualTo(0);
        }

        @Test
        @DisplayName("no matching customers → empty content with zeroed summary")
        void noResults() {
            var page = new PageImpl<Customer>(List.of(), PageRequest.of(0, 20), 0);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(repository.count(any(Specification.class))).thenReturn(0L);

            ReportPage<CustomerReportRow, CustomerReportSummary> result = service.getReport(
                    LocalDate.now().minusDays(7), LocalDate.now(), CustomerStatus.SUSPENDED, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalCustomers()).isZero();
            assertThat(result.getSummary().getActiveCustomers()).isZero();
            assertThat(result.getSummary().getInactiveCustomers()).isZero();
            assertThat(result.getSummary().getSuspendedCustomers()).isZero();
        }
    }

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("keyword + status + date range all combine into one query")
        void allFiltersCombine() {
            Customer customer = buildCustomer();
            var page = new PageImpl<>(List.of(customer), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toResponse(customer)).thenReturn(buildResponse());

            Page<CustomerResponse> result = service.search(
                    "kafka", LocalDate.now().minusDays(7), LocalDate.now(), CustomerStatus.ACTIVE, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("blank keyword → keyword predicate is not applied")
        void blankKeyword_notApplied() {
            var page = new PageImpl<Customer>(List.of(), PageRequest.of(0, 20), 0);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

            Page<CustomerResponse> result = service.search("   ", null, null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("no filters → returns all non-deleted customers")
        void noFilters() {
            Customer customer = buildCustomer();
            var page = new PageImpl<>(List.of(customer), PageRequest.of(0, 20), 1);
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(mapper.toResponse(customer)).thenReturn(buildResponse());

            Page<CustomerResponse> result = service.search(null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("export()")
    class Export {

        @Test
        @DisplayName("CSV format → streams matching customers as CSV rows")
        void csv_streamsMatchingRows() throws Exception {
            Customer customer = buildCustomer();
            var firstPage = new PageImpl<>(List.of(customer), PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("createdAt").ascending()), 1);
            var emptyPage = new PageImpl<Customer>(List.of());
            when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(firstPage, emptyPage);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, "kafka", null, null, CustomerStatus.ACTIVE, "createdAt", true);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content).contains("Customer Code").contains("CUST-001000").contains("Kafka");
        }

        @Test
        @DisplayName("no matching customers → writes header only, no rows")
        void noMatches_writesHeaderOnly() throws Exception {
            when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<Customer>(List.of()));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, null, null, null, null, "createdAt", false);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content.trim()).isEqualTo(
                    "Customer Code,First Name,Last Name,Mobile,Email,Status,Created At");
        }
    }

    @Nested
    @DisplayName("getGrowthTrend()")
    class GetGrowthTrend {
        @Test
        @DisplayName("maps already-aggregated repository rows into trend points")
        void mapsRows() {
            CustomerRepository.CustomerGrowthRow row = mock(CustomerRepository.CustomerGrowthRow.class);
            when(row.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(row.getNewCustomers()).thenReturn(5L);
            when(repository.findCustomerGrowth(eq("day"), any(), any())).thenReturn(List.of(row));

            TrendSeries<CustomerGrowthPoint> result = service.getGrowthTrend(
                    Granularity.DAILY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertThat(result.getGranularity()).isEqualTo(Granularity.DAILY);
            assertThat(result.getPoints()).hasSize(1);
            assertThat(result.getPoints().get(0).getPeriod()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(result.getPoints().get(0).getNewCustomers()).isEqualTo(5L);
            verify(repository).findCustomerGrowth("day",
                    LocalDate.of(2026, 1, 1).atStartOfDay(), LocalDate.of(2026, 2, 1).atStartOfDay());
        }

        @Test
        @DisplayName("no rows → empty points list")
        void noRows_emptyPoints() {
            when(repository.findCustomerGrowth(eq("month"), any(), any())).thenReturn(List.of());

            TrendSeries<CustomerGrowthPoint> result = service.getGrowthTrend(Granularity.MONTHLY, null, null);

            assertThat(result.getPoints()).isEmpty();
            verify(repository).findCustomerGrowth("month", null, null);
        }
    }
}
