package com.farm2home.customer.service.impl;

import com.farm2home.common.core.analytics.CustomerGrowthPoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.common.core.constants.FileConstants;
import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.reports.CustomerReportRow;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.BatchSupplier;
import com.farm2home.common.export.ExportColumn;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.export.TabularExporterFactory;
import com.farm2home.common.web.storage.FileStorageService;
import com.farm2home.customer.domain.entity.Customer;
import com.farm2home.customer.domain.enums.CustomerStatus;
import com.farm2home.customer.domain.repository.CustomerRepository;
import com.farm2home.customer.domain.repository.CustomerSpecifications;
import com.farm2home.customer.dto.request.UpdateCustomerRequest;
import com.farm2home.customer.dto.response.CustomerResponse;
import com.farm2home.customer.exception.CustomerException;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl {

    private static final String UPLOAD_CATEGORY = FileConstants.CATEGORY_CUSTOMERS;

    private final CustomerRepository repository;
    private final CustomerMapper mapper;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        return mapper.toResponse(getCustomer(id));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> findAll(Pageable pageable) {
        return repository.findAllByDeletedFalse(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerSummaryResponse getSummary() {
        return CustomerSummaryResponse.builder().totalCustomers(repository.countByDeletedFalse()).build();
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Customer")
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = getCustomer(id);
        mapper.updateEntityFromRequest(request, customer);
        return mapper.toResponse(repository.save(customer));
    }

    @Transactional
    @Audited(action = AuditAction.UPLOAD, entityType = "Customer")
    public CustomerResponse uploadProfileImage(UUID id, MultipartFile file) {
        Customer customer = getCustomer(id);
        String relativePath = fileStorageService.store(file, UPLOAD_CATEGORY);
        customer.setProfileImageUrl("/uploads/" + relativePath);
        return mapper.toResponse(repository.save(customer));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "Customer")
    public void delete(UUID id) {
        Customer customer = getCustomer(id);
        if (customer.getStatus() == CustomerStatus.ACTIVE) {
            throw new CustomerException(
                    "Cannot delete an active customer. Set status to INACTIVE first.");
        }
        customer.setDeleted(true);
        repository.save(customer);
    }

    private Customer getCustomer(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    @Transactional(readOnly = true)
    public ReportPage<CustomerReportRow, CustomerReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            CustomerStatus status, Pageable pageable) {
        Specification<Customer> dateSpec = Specification.where(CustomerSpecifications.notDeleted());
        if (dateFrom != null || dateTo != null) {
            dateSpec = dateSpec.and(CustomerSpecifications.createdBetween(dateFrom, dateTo));
        }
        Specification<Customer> spec = status != null ? dateSpec.and(CustomerSpecifications.hasStatus(status)) : dateSpec;

        Page<Customer> page = repository.findAll(spec, pageable);
        List<CustomerReportRow> rows = page.getContent().stream()
                .map(c -> CustomerReportRow.builder()
                        .customerId(c.getId())
                        .customerCode(c.getCustomerCode())
                        .name(c.getFirstName() + " " + c.getLastName())
                        .mobile(c.getMobile())
                        .email(c.getEmail())
                        .status(c.getStatus().name())
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();

        CustomerReportSummary summary = CustomerReportSummary.builder()
                .totalCustomers(page.getTotalElements())
                .activeCustomers(repository.count(dateSpec.and(CustomerSpecifications.hasStatus(CustomerStatus.ACTIVE))))
                .inactiveCustomers(repository.count(dateSpec.and(CustomerSpecifications.hasStatus(CustomerStatus.INACTIVE))))
                .suspendedCustomers(repository.count(dateSpec.and(CustomerSpecifications.hasStatus(CustomerStatus.SUSPENDED))))
                .build();

        return ReportPage.<CustomerReportRow, CustomerReportSummary>builder()
                .content(rows)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .summary(summary)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String keyword, LocalDate dateFrom, LocalDate dateTo,
            CustomerStatus status, Pageable pageable) {
        Specification<Customer> spec = buildSearchSpecification(keyword, dateFrom, dateTo, status);
        return repository.findAll(spec, pageable).map(mapper::toResponse);
    }

    /** Shared by both {@link #search} and {@link #export} so the two always see the exact same
     *  filtered result set. */
    private Specification<Customer> buildSearchSpecification(String keyword, LocalDate dateFrom, LocalDate dateTo,
            CustomerStatus status) {
        Specification<Customer> spec = Specification.where(CustomerSpecifications.notDeleted());
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(CustomerSpecifications.hasKeyword(keyword));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(CustomerSpecifications.createdBetween(dateFrom, dateTo));
        }
        if (status != null) {
            spec = spec.and(CustomerSpecifications.hasStatus(status));
        }
        return spec;
    }

    /** Streams matching customers straight to {@code out} in the requested file format, one
     *  bounded page at a time, so exporting a very large customer base never requires holding
     *  the full result set in memory. Each batch fetch runs in its own short-lived Spring Data
     *  transaction (this method is deliberately NOT wrapped in a single @Transactional so a
     *  slow export doesn't pin one DB connection for its entire duration). Runs on the async
     *  StreamingResponseBody dispatch thread, not the original request thread. */
    public void export(ExportFormat format, OutputStream out, String keyword, LocalDate dateFrom, LocalDate dateTo,
            CustomerStatus status, String sortBy, boolean ascending) throws IOException {
        Specification<Customer> spec = buildSearchSpecification(keyword, dateFrom, dateTo, status);
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        List<ExportColumn<Customer>> columns = List.of(
                new ExportColumn<>("Customer Code", Customer::getCustomerCode),
                new ExportColumn<>("First Name", Customer::getFirstName),
                new ExportColumn<>("Last Name", Customer::getLastName),
                new ExportColumn<>("Mobile", Customer::getMobile),
                new ExportColumn<>("Email", c -> c.getEmail() == null ? "" : c.getEmail()),
                new ExportColumn<>("Status", c -> c.getStatus().name()),
                new ExportColumn<>("Created At", c -> c.getCreatedAt() == null ? "" : c.getCreatedAt().toString()));

        BatchSupplier<Customer> supplier = (page, size) ->
                repository.findAll(spec, PageRequest.of(page, size, sort)).getContent();

        TabularExporterFactory.<Customer>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, supplier);
    }

    /** Customer Growth: the date_trunc GROUP BY/COUNT runs entirely in Postgres (see
     *  CustomerRepository.findCustomerGrowth) - this method only maps the already-aggregated,
     *  one-row-per-period result into the response DTO. */
    @Transactional(readOnly = true)
    public TrendSeries<CustomerGrowthPoint> getGrowthTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime start = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime endExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;

        List<CustomerGrowthPoint> points = repository.findCustomerGrowth(granularity.getSqlUnit(), start, endExclusive)
                .stream()
                .map(row -> CustomerGrowthPoint.builder().period(row.getPeriod()).newCustomers(row.getNewCustomers()).build())
                .toList();

        return TrendSeries.<CustomerGrowthPoint>builder()
                .granularity(granularity).from(dateFrom).to(dateTo).points(points).build();
    }
}
