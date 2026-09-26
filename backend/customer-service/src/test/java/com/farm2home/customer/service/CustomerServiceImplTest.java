package com.farm2home.customer.service;

import com.farm2home.common.core.analytics.CustomerGrowthPoint;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.CustomerSummaryResponse;
import com.farm2home.common.core.reports.CustomerReportRow;
import com.farm2home.common.core.reports.CustomerReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.storage.FileStorageService;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.domain.entity.Customer;
import com.farm2home.customer.domain.entity.CustomerAddress;
import com.farm2home.customer.domain.entity.LocationCity;
import com.farm2home.customer.domain.entity.LocationDistrict;
import com.farm2home.customer.domain.entity.LocationState;
import com.farm2home.customer.domain.enums.CustomerStatus;
import com.farm2home.customer.domain.repository.CustomerAddressRepository;
import com.farm2home.customer.domain.repository.CustomerRepository;
import com.farm2home.customer.domain.repository.LocationCityRepository;
import com.farm2home.customer.domain.repository.LocationDistrictRepository;
import com.farm2home.customer.domain.repository.LocationStateRepository;
import com.farm2home.customer.dto.request.CreateAddressRequest;
import com.farm2home.customer.dto.request.UpdateAddressRequest;
import com.farm2home.customer.dto.request.UpdateCustomerRequest;
import com.farm2home.customer.dto.response.AddressResponse;
import com.farm2home.customer.dto.response.CustomerResponse;
import com.farm2home.customer.exception.CustomerException;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.mapper.CustomerAddressMapper;
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
import java.math.BigDecimal;
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
    @Mock private CustomerAddressRepository addressRepository;
    @Mock private CustomerAddressMapper addressMapper;
    @Mock private LocationStateRepository locationStateRepository;
    @Mock private LocationDistrictRepository locationDistrictRepository;
    @Mock private LocationCityRepository locationCityRepository;

    @InjectMocks private CustomerServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UserPrincipal ownerPrincipal = new UserPrincipal(customerId, "9876543210", java.util.Set.of("CUSTOMER"));
    private final UserPrincipal adminPrincipal = new UserPrincipal(UUID.randomUUID(), "9000000001", java.util.Set.of(SecurityConstants.ROLE_SUPER_ADMIN));
    private final UserPrincipal strangerPrincipal = new UserPrincipal(UUID.randomUUID(), "9111111111", java.util.Set.of("CUSTOMER"));
    // FARM_MANAGER counts as admin here too - see UserPrincipal.isAdmin()'s comment for why
    // (subscription-service treats FARM_MANAGER as admin, so they need to resolve a customer's
    // name/mobile while managing that customer's subscriptions).
    private final UserPrincipal farmManagerPrincipal = new UserPrincipal(UUID.randomUUID(), "9222222222", java.util.Set.of(SecurityConstants.ROLE_FARM_MANAGER));

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

    private CustomerAddress buildAddress() {
        return CustomerAddress.builder()
                .id(addressId).customerId(customerId).addressLine1("402, Block A").city("Mumbai")
                .state("Maharashtra").pincode("400001").defaultAddress(true).deleted(false).build();
    }

    private AddressResponse buildAddressResponse() {
        return AddressResponse.builder().id(addressId).addressLine1("402, Block A").city("Mumbai")
                .state("Maharashtra").pincode("400001").defaultAddress(true).build();
    }

    @Nested @DisplayName("findById()")
    class FindById {
        @Test
        @DisplayName("caller is the owner → returns response")
        void ownerCanAccess() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findById(customerId, ownerPrincipal).getId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("caller is SUPER_ADMIN, not the owner → returns response")
        void adminCanAccessAnyCustomer() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findById(customerId, adminPrincipal).getId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("caller is FARM_MANAGER, not the owner → returns response")
        void farmManagerCanAccessAnyCustomer() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(mapper.toResponse(any())).thenReturn(buildResponse());

            assertThat(service.findById(customerId, farmManagerPrincipal).getId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("caller is neither the owner nor an admin → 404, same as an unknown id")
        void strangerGetsNotFound() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));

            assertThatThrownBy(() -> service.findById(customerId, strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(mapper, never()).toResponse(any());
        }

        @Test
        @DisplayName("missing customer → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(customerId, ownerPrincipal))
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
        @DisplayName("owner updates their own profile → applies changes and saves")
        void happyPath() {
            Customer entity = buildCustomer();
            UpdateCustomerRequest req = new UpdateCustomerRequest();
            req.setEmail("new@example.com");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.update(customerId, req, ownerPrincipal);

            verify(mapper).updateEntityFromRequest(req, entity);
            verify(repository).save(entity);
        }

        @Test
        @DisplayName("caller is neither the owner nor an admin → 404, no changes applied")
        void strangerCannotUpdate() {
            Customer entity = buildCustomer();
            UpdateCustomerRequest req = new UpdateCustomerRequest();
            req.setEmail("hijacked@example.com");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.update(customerId, req, strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(mapper, never()).updateEntityFromRequest(any(), any());
            verify(repository, never()).save(any());
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
        @DisplayName("owner uploads their own image → stores and sets profileImageUrl")
        void happyPath() {
            Customer entity = buildCustomer();
            MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));
            when(fileStorageService.store(file, "customers")).thenReturn("customers/uuid.jpg");
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(buildResponse());

            service.uploadProfileImage(customerId, file, ownerPrincipal);

            assertThat(entity.getProfileImageUrl()).isEqualTo("/uploads/customers/uuid.jpg");
            verify(repository).save(entity);
        }

        @Test
        @DisplayName("caller is neither the owner nor an admin → 404, nothing stored")
        void strangerCannotUpload() {
            Customer entity = buildCustomer();
            MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[]{1, 2, 3});
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.uploadProfileImage(customerId, file, strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(fileStorageService, never()).store(any(), any());
            verify(repository, never()).save(any());
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

    @Nested @DisplayName("getAddresses()")
    class GetAddresses {
        @Test
        @DisplayName("owner → returns their addresses")
        void ownerCanAccess() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findAllByCustomerIdAndDeletedFalse(customerId)).thenReturn(List.of(buildAddress()));
            when(addressMapper.toResponse(any())).thenReturn(buildAddressResponse());

            assertThat(service.getAddresses(customerId, ownerPrincipal)).hasSize(1);
        }

        @Test
        @DisplayName("stranger → 404, same as an unknown customer")
        void strangerGetsNotFound() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));

            assertThatThrownBy(() -> service.getAddresses(customerId, strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(addressRepository, never()).findAllByCustomerIdAndDeletedFalse(any());
        }
    }

    @Nested @DisplayName("addAddress()")
    class AddAddress {
        @Test
        @DisplayName("first address for the customer -> becomes the default")
        void firstAddress_becomesDefault() {
            CreateAddressRequest req = new CreateAddressRequest();
            CustomerAddress entity = buildAddress();
            entity.setDefaultAddress(false);
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressMapper.toEntity(req)).thenReturn(entity);
            when(addressRepository.existsByCustomerIdAndDeletedFalse(customerId)).thenReturn(false);
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.addAddress(customerId, req);

            assertThat(entity.isDefaultAddress()).isTrue();
        }

        @Test
        @DisplayName("customer already has an address -> new one is not the default (only one default row can ever exist)")
        void laterAddress_notDefault() {
            CreateAddressRequest req = new CreateAddressRequest();
            CustomerAddress entity = buildAddress();
            entity.setDefaultAddress(true);
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressMapper.toEntity(req)).thenReturn(entity);
            when(addressRepository.existsByCustomerIdAndDeletedFalse(customerId)).thenReturn(true);
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.addAddress(customerId, req);

            assertThat(entity.isDefaultAddress()).isFalse();
        }
    }

    @Nested @DisplayName("addAddressScoped()")
    class AddAddressScoped {
        @Test
        @DisplayName("first address for the customer → becomes the default")
        void firstAddress_becomesDefault() {
            CreateAddressRequest req = new CreateAddressRequest();
            CustomerAddress entity = buildAddress();
            entity.setDefaultAddress(false);
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressMapper.toEntity(req)).thenReturn(entity);
            when(addressRepository.existsByCustomerIdAndDeletedFalse(customerId)).thenReturn(false);
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.addAddressScoped(customerId, req, ownerPrincipal);

            assertThat(entity.isDefaultAddress()).isTrue();
        }

        @Test
        @DisplayName("customer already has an address → new one is not the default")
        void laterAddress_notDefault() {
            CreateAddressRequest req = new CreateAddressRequest();
            CustomerAddress entity = buildAddress();
            entity.setDefaultAddress(true);
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressMapper.toEntity(req)).thenReturn(entity);
            when(addressRepository.existsByCustomerIdAndDeletedFalse(customerId)).thenReturn(true);
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.addAddressScoped(customerId, req, ownerPrincipal);

            assertThat(entity.isDefaultAddress()).isFalse();
        }

        @Test
        @DisplayName("stranger → 404, nothing saved")
        void strangerCannotAdd() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));

            assertThatThrownBy(() -> service.addAddressScoped(customerId, new CreateAddressRequest(), strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(addressRepository, never()).save(any());
        }
    }

    @Nested @DisplayName("updateAddress()")
    class UpdateAddress {
        @Test
        @DisplayName("owner updates their own address → applies changes and saves")
        void happyPath() {
            CustomerAddress entity = buildAddress();
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setCity("Pune");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.updateAddress(customerId, addressId, req, ownerPrincipal);

            verify(addressMapper).updateEntityFromRequest(req, entity);
            verify(addressRepository).save(entity);
        }

        @Test
        @DisplayName("stranger → 404, no changes applied")
        void strangerCannotUpdate() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));

            assertThatThrownBy(() -> service.updateAddress(customerId, addressId, new UpdateAddressRequest(), strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(addressRepository, never()).save(any());
        }

        @Test
        @DisplayName("address belongs to a different customer → 404")
        void addressBelongsToAnotherCustomer_notFound() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAddress(customerId, addressId, new UpdateAddressRequest(), ownerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("clearCoordinates=true → nulls out existing latitude/longitude before the mapper runs")
        void clearCoordinates_nullsExistingCoordinates() {
            CustomerAddress entity = buildAddress();
            entity.setLatitude(new BigDecimal("19.07600000"));
            entity.setLongitude(new BigDecimal("72.87770000"));
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setAddressLine1("A new street entirely");
            req.setClearCoordinates(true);
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.updateAddress(customerId, addressId, req, ownerPrincipal);

            // addressMapper.updateEntityFromRequest is mocked (no-op), so only this method's own
            // explicit clearing code could have nulled these - proves the clear happens
            // independently of whatever the real mapper would later apply.
            assertThat(entity.getLatitude()).isNull();
            assertThat(entity.getLongitude()).isNull();
        }

        @Test
        @DisplayName("clearCoordinates=true with fresh coordinates also in the request → new values win over the clear")
        void clearCoordinates_doesNotBlockFreshlyProvidedCoordinates() {
            CustomerAddress entity = buildAddress();
            entity.setLatitude(new BigDecimal("19.07600000"));
            entity.setLongitude(new BigDecimal("72.87770000"));
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setClearCoordinates(true);
            req.setLatitude(new BigDecimal("13.62750000"));
            req.setLongitude(new BigDecimal("78.96910000"));
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());
            // addressMapper is a mock (a no-op by default) everywhere else in this class, so this
            // simulates just the one piece of the real mapper's NullValuePropertyMappingStrategy.
            // IGNORE semantics this test needs - a non-null request field overwrites the entity -
            // to prove the service clears BEFORE the mapper runs, so a non-null value in the same
            // request still wins over that clear.
            doAnswer(invocation -> {
                UpdateAddressRequest r = invocation.getArgument(0);
                CustomerAddress a = invocation.getArgument(1);
                if (r.getLatitude() != null) a.setLatitude(r.getLatitude());
                if (r.getLongitude() != null) a.setLongitude(r.getLongitude());
                return null;
            }).when(addressMapper).updateEntityFromRequest(req, entity);

            service.updateAddress(customerId, addressId, req, ownerPrincipal);

            assertThat(entity.getLatitude()).isEqualByComparingTo("13.6275");
            assertThat(entity.getLongitude()).isEqualByComparingTo("78.9691");
        }

        private LocationState buildState(UUID id) {
            return LocationState.builder().id(id).name("Andhra Pradesh").code("AP").active(true).build();
        }

        private LocationDistrict buildDistrict(UUID id, UUID stateId) {
            return LocationDistrict.builder().id(id).stateId(stateId).name("Annamayya").code("AP-16").active(true).build();
        }

        @Test
        @DisplayName("valid state/district/city combination → accepted, address updated")
        void validLocationHierarchy_accepted() {
            UUID stateId = UUID.randomUUID();
            UUID districtId = UUID.randomUUID();
            LocationState state = buildState(stateId);
            LocationDistrict district = buildDistrict(districtId, stateId);
            LocationCity city = LocationCity.builder().id(UUID.randomUUID()).districtId(districtId).name("Pileru").active(true).build();
            CustomerAddress entity = buildAddress();
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setState("Andhra Pradesh");
            req.setDistrict("Annamayya");
            req.setCity("Pileru");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));
            when(locationStateRepository.findByNameIgnoreCaseAndActiveTrue("Andhra Pradesh")).thenReturn(Optional.of(state));
            when(locationDistrictRepository.findByStateIdAndNameIgnoreCaseAndActiveTrue(stateId, "Annamayya")).thenReturn(Optional.of(district));
            when(locationCityRepository.findByDistrictIdAndNameIgnoreCaseAndActiveTrue(districtId, "Pileru")).thenReturn(Optional.of(city));
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.updateAddress(customerId, addressId, req, ownerPrincipal);

            // "Valid update persists correctly" - not just that save() was called on some entity,
            // but that the mapper step which actually copies the new values onto it ran with
            // exactly this request/entity pair, and that the saved entity is the one returned.
            verify(addressMapper).updateEntityFromRequest(req, entity);
            verify(addressRepository).save(entity);
        }

        @Test
        @DisplayName("district that doesn't belong to the given state → rejected, address left untouched")
        void districtNotInState_rejected() {
            UUID stateId = UUID.randomUUID();
            LocationState state = buildState(stateId);
            CustomerAddress entity = buildAddress();
            String originalCity = entity.getCity();
            String originalState = entity.getState();
            String originalDistrict = entity.getDistrict();
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setState("Andhra Pradesh");
            req.setDistrict("Some Other District");
            req.setCity("Pileru");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));
            when(locationStateRepository.findByNameIgnoreCaseAndActiveTrue("Andhra Pradesh")).thenReturn(Optional.of(state));
            when(locationDistrictRepository.findByStateIdAndNameIgnoreCaseAndActiveTrue(stateId, "Some Other District")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAddress(customerId, addressId, req, ownerPrincipal))
                    .isInstanceOf(CustomerException.class);
            // "Invalid hierarchy does not mutate the address" - proven directly on the entity's own
            // fields (rejection happens before the mapper ever runs), not just inferred from save()
            // never being called.
            assertThat(entity.getCity()).isEqualTo(originalCity);
            assertThat(entity.getState()).isEqualTo(originalState);
            assertThat(entity.getDistrict()).isEqualTo(originalDistrict);
            verify(addressRepository, never()).save(any());
            verify(addressMapper, never()).updateEntityFromRequest(any(), any());
        }

        @Test
        @DisplayName("city that doesn't belong to the given district → rejected, address left untouched")
        void cityNotInDistrict_rejected() {
            UUID stateId = UUID.randomUUID();
            UUID districtId = UUID.randomUUID();
            LocationState state = buildState(stateId);
            LocationDistrict district = buildDistrict(districtId, stateId);
            CustomerAddress entity = buildAddress();
            String originalCity = entity.getCity();
            String originalState = entity.getState();
            String originalDistrict = entity.getDistrict();
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setState("Andhra Pradesh");
            req.setDistrict("Annamayya");
            req.setCity("Not A Real City");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));
            when(locationStateRepository.findByNameIgnoreCaseAndActiveTrue("Andhra Pradesh")).thenReturn(Optional.of(state));
            when(locationDistrictRepository.findByStateIdAndNameIgnoreCaseAndActiveTrue(stateId, "Annamayya")).thenReturn(Optional.of(district));
            when(locationCityRepository.findByDistrictIdAndNameIgnoreCaseAndActiveTrue(districtId, "Not A Real City")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAddress(customerId, addressId, req, ownerPrincipal))
                    .isInstanceOf(CustomerException.class);
            assertThat(entity.getCity()).isEqualTo(originalCity);
            assertThat(entity.getState()).isEqualTo(originalState);
            assertThat(entity.getDistrict()).isEqualTo(originalDistrict);
            verify(addressRepository, never()).save(any());
            verify(addressMapper, never()).updateEntityFromRequest(any(), any());
        }

        @Test
        @DisplayName("request changes only city/district, omitting state → validated against the "
                + "address's own EXISTING state, not left null")
        void partialUpdate_fallsBackToExistingStateForValidation() {
            UUID stateId = UUID.randomUUID();
            UUID districtId = UUID.randomUUID();
            LocationState state = buildState(stateId);
            LocationDistrict district = buildDistrict(districtId, stateId);
            LocationCity city = LocationCity.builder().id(UUID.randomUUID()).districtId(districtId).name("Pileru").active(true).build();
            CustomerAddress entity = buildAddress();
            entity.setState("Andhra Pradesh"); // the address's own existing value - request below never sets it
            UpdateAddressRequest req = new UpdateAddressRequest();
            req.setDistrict("Annamayya");
            req.setCity("Pileru");
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));
            when(locationStateRepository.findByNameIgnoreCaseAndActiveTrue("Andhra Pradesh")).thenReturn(Optional.of(state));
            when(locationDistrictRepository.findByStateIdAndNameIgnoreCaseAndActiveTrue(stateId, "Annamayya")).thenReturn(Optional.of(district));
            when(locationCityRepository.findByDistrictIdAndNameIgnoreCaseAndActiveTrue(districtId, "Pileru")).thenReturn(Optional.of(city));
            when(addressRepository.save(entity)).thenReturn(entity);
            when(addressMapper.toResponse(entity)).thenReturn(buildAddressResponse());

            service.updateAddress(customerId, addressId, req, ownerPrincipal);

            verify(locationStateRepository).findByNameIgnoreCaseAndActiveTrue("Andhra Pradesh");
            verify(addressRepository).save(entity);
        }
    }

    @Nested @DisplayName("deleteAddress()")
    class DeleteAddress {
        @Test
        @DisplayName("owner deletes their own address → sets deleted=true")
        void happyPath() {
            CustomerAddress entity = buildAddress();
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)).thenReturn(Optional.of(entity));

            service.deleteAddress(customerId, addressId, ownerPrincipal);

            assertThat(entity.isDeleted()).isTrue();
            verify(addressRepository).save(entity);
        }

        @Test
        @DisplayName("stranger → 404, nothing deleted")
        void strangerCannotDelete() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));

            assertThatThrownBy(() -> service.deleteAddress(customerId, addressId, strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(addressRepository, never()).save(any());
        }
    }

    @Nested @DisplayName("setDefaultAddress()")
    class SetDefaultAddress {
        @Test
        @DisplayName("unsets every other address's default flag, sets the target's")
        void happyPath() {
            CustomerAddress target = buildAddress();
            target.setDefaultAddress(false);
            CustomerAddress other = CustomerAddress.builder()
                    .id(UUID.randomUUID()).customerId(customerId).addressLine1("Other").city("Pune")
                    .state("Maharashtra").pincode("411001").defaultAddress(true).deleted(false).build();
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findAllByCustomerIdAndDeletedFalse(customerId)).thenReturn(List.of(target, other));
            when(addressMapper.toResponse(target)).thenReturn(buildAddressResponse());

            service.setDefaultAddress(customerId, addressId, ownerPrincipal);

            assertThat(target.isDefaultAddress()).isTrue();
            assertThat(other.isDefaultAddress()).isFalse();
            verify(addressRepository).saveAll(List.of(target, other));
        }

        @Test
        @DisplayName("address not found among the customer's addresses → 404")
        void addressNotFound_throws() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));
            when(addressRepository.findAllByCustomerIdAndDeletedFalse(customerId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.setDefaultAddress(customerId, addressId, ownerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("stranger → 404")
        void strangerCannotSetDefault() {
            when(repository.findByIdAndDeletedFalse(customerId)).thenReturn(Optional.of(buildCustomer()));

            assertThatThrownBy(() -> service.setDefaultAddress(customerId, addressId, strangerPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(addressRepository, never()).saveAll(any());
        }
    }
}
