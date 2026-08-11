package com.farm2home.invoice.service;

import com.farm2home.common.web.exception.ConflictException;
import com.farm2home.common.web.exception.ResourceNotFoundException;
import com.farm2home.invoice.client.*;
import com.farm2home.invoice.config.UserPrincipal;
import com.farm2home.invoice.domain.entity.Invoice;
import com.farm2home.invoice.domain.repository.InvoiceRepository;
import com.farm2home.invoice.dto.response.InvoiceResponse;
import com.farm2home.invoice.dto.response.InvoiceSummaryResponse;
import com.farm2home.invoice.service.impl.InvoicePdfRenderer;
import com.farm2home.invoice.service.impl.InvoiceServiceImpl;
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
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private PaymentServiceClient paymentServiceClient;
    @Mock private CustomerServiceClient customerServiceClient;
    @Mock private InvoicePdfRenderer pdfRenderer;

    @InjectMocks private InvoiceServiceImpl service;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();

    private OrderDetailResponse buildOrder() {
        OrderDetailResponse order = new OrderDetailResponse();
        order.setId(orderId);
        order.setOrderNumber("ORD-2026-100001");
        order.setCustomerId(customerId);
        order.setOrderDate(LocalDate.of(2026, 8, 9));
        order.setStatus("DELIVERED");
        order.setTotalAmount(new BigDecimal("120.00"));
        OrderDetailResponse.Item item = new OrderDetailResponse.Item();
        item.setMilkType("FULL_CREAM");
        item.setQuantity(new BigDecimal("2.00"));
        item.setUnitPrice(new BigDecimal("60.00"));
        item.setTotalPrice(new BigDecimal("120.00"));
        order.setItems(List.of(item));
        return order;
    }

    private Invoice buildInvoice() {
        return Invoice.builder()
                .id(invoiceId).invoiceNumber("INV-2026-000001").orderId(orderId).customerId(customerId)
                .subtotal(new BigDecimal("120.00")).totalAmount(new BigDecimal("120.00"))
                .issueDate(LocalDate.of(2026, 8, 10)).build();
    }

    private void stubEmptyEnrichment() {
        lenient().when(paymentServiceClient.getPrimaryPaymentForOrder(any())).thenReturn(Mono.empty());
        lenient().when(customerServiceClient.getCustomer(any())).thenReturn(Mono.empty());
        lenient().when(customerServiceClient.getDefaultAddress(any())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("no existing invoice for the order → creates and returns one")
        void createsInvoice() {
            when(invoiceRepository.existsByOrderIdAndDeletedFalse(orderId)).thenReturn(false);
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(buildOrder()));
            when(invoiceRepository.nextInvoiceNumber()).thenReturn(1L);
            when(invoiceRepository.save(any())).thenAnswer(inv -> {
                Invoice i = inv.getArgument(0);
                i.setId(invoiceId);
                return i;
            });
            stubEmptyEnrichment();

            InvoiceResponse response = service.generate(orderId);

            assertThat(response.getOrderId()).isEqualTo(orderId);
            assertThat(response.getSubtotal()).isEqualByComparingTo("120.00");
            assertThat(response.getTotalAmount()).isEqualByComparingTo("120.00");
            assertThat(response.getInvoiceNumber()).startsWith("INV-");
            assertThat(response.getItems()).hasSize(1);
            verify(invoiceRepository).save(any());
        }

        @Test
        @DisplayName("invoice already exists for the order → 409 ConflictException, no save")
        void alreadyExists_conflict() {
            when(invoiceRepository.existsByOrderIdAndDeletedFalse(orderId)).thenReturn(true);

            assertThatThrownBy(() -> service.generate(orderId)).isInstanceOf(ConflictException.class);

            verify(invoiceRepository, never()).save(any());
            verifyNoInteractions(orderServiceClient);
        }

        @Test
        @DisplayName("order does not exist → 404 ResourceNotFoundException, no save")
        void orderNotFound() {
            when(invoiceRepository.existsByOrderIdAndDeletedFalse(orderId)).thenReturn(false);
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.empty());

            assertThatThrownBy(() -> service.generate(orderId)).isInstanceOf(ResourceNotFoundException.class);

            verify(invoiceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("admin → looks up by id only (any customer)")
        void admin_findsAny() {
            UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of("SUPER_ADMIN"));
            when(invoiceRepository.findByIdAndDeletedFalse(invoiceId)).thenReturn(Optional.of(buildInvoice()));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(buildOrder()));
            stubEmptyEnrichment();

            InvoiceResponse response = service.findById(invoiceId, admin);

            assertThat(response.getId()).isEqualTo(invoiceId);
            verify(invoiceRepository).findByIdAndDeletedFalse(invoiceId);
            verify(invoiceRepository, never()).findByIdAndCustomerIdAndDeletedFalse(any(), any());
        }

        @Test
        @DisplayName("non-admin → scoped to caller's own customerId")
        void customer_scopedToOwn() {
            UserPrincipal customer = new UserPrincipal(customerId, "9876543210", Set.of("CUSTOMER"));
            when(invoiceRepository.findByIdAndCustomerIdAndDeletedFalse(invoiceId, customerId))
                    .thenReturn(Optional.of(buildInvoice()));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(buildOrder()));
            stubEmptyEnrichment();

            service.findById(invoiceId, customer);

            verify(invoiceRepository).findByIdAndCustomerIdAndDeletedFalse(invoiceId, customerId);
        }

        @Test
        @DisplayName("not found / not owned → 404, not an information-leaking 403")
        void notFound_throws404() {
            UserPrincipal customer = new UserPrincipal(customerId, "9876543210", Set.of("CUSTOMER"));
            when(invoiceRepository.findByIdAndCustomerIdAndDeletedFalse(invoiceId, customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(invoiceId, customer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("search() / findMine()")
    class Listing {

        @Test
        @DisplayName("search() enriches each row with order number and customer name")
        void search_enrichesRows() {
            Invoice invoice = buildInvoice();
            when(invoiceRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Invoice>>any(), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(0, 20), 1));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(buildOrder()));
            CustomerDetailResponse customer = new CustomerDetailResponse();
            customer.setFirstName("Asha");
            customer.setLastName("Rao");
            when(customerServiceClient.getCustomer(customerId)).thenReturn(Mono.just(customer));

            Page<InvoiceSummaryResponse> page = service.search(null, null, null, null, PageRequest.of(0, 20));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getOrderNumber()).isEqualTo("ORD-2026-100001");
            assertThat(page.getContent().get(0).getCustomerName()).isEqualTo("Asha Rao");
        }

        @Test
        @DisplayName("findMine() only queries invoices for the given customerId")
        void findMine_scopedToCustomer() {
            when(invoiceRepository.findAllByCustomerIdAndDeletedFalse(eq(customerId), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            service.findMine(customerId, PageRequest.of(0, 20));

            verify(invoiceRepository).findAllByCustomerIdAndDeletedFalse(eq(customerId), any());
        }
    }

    @Nested
    @DisplayName("generatePdf()")
    class GeneratePdf {

        @Test
        @DisplayName("delegates to the renderer with the composed invoice")
        void rendersComposedInvoice() {
            UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of("SUPER_ADMIN"));
            when(invoiceRepository.findByIdAndDeletedFalse(invoiceId)).thenReturn(Optional.of(buildInvoice()));
            when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(buildOrder()));
            stubEmptyEnrichment();
            byte[] fakePdf = {1, 2, 3};
            when(pdfRenderer.render(any())).thenReturn(fakePdf);

            byte[] result = service.generatePdf(invoiceId, admin);

            assertThat(result).isEqualTo(fakePdf);
            verify(pdfRenderer).render(any());
        }
    }
}
