package com.farm2home.invoice.service.impl;

import com.farm2home.common.web.exception.ConflictException;
import com.farm2home.common.web.exception.ResourceNotFoundException;
import com.farm2home.invoice.client.AddressDetailResponse;
import com.farm2home.invoice.client.CustomerDetailResponse;
import com.farm2home.invoice.client.CustomerServiceClient;
import com.farm2home.invoice.client.OrderDetailResponse;
import com.farm2home.invoice.client.OrderServiceClient;
import com.farm2home.invoice.client.PaymentDetailResponse;
import com.farm2home.invoice.client.PaymentServiceClient;
import com.farm2home.invoice.config.UserPrincipal;
import com.farm2home.invoice.domain.entity.Invoice;
import com.farm2home.invoice.domain.repository.InvoiceRepository;
import com.farm2home.invoice.domain.repository.InvoiceSpecifications;
import com.farm2home.invoice.dto.response.InvoiceResponse;
import com.farm2home.invoice.dto.response.InvoiceSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl {

    private final InvoiceRepository invoiceRepository;
    private final OrderServiceClient orderServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final InvoicePdfRenderer pdfRenderer;

    @Transactional
    public InvoiceResponse generate(UUID orderId) {
        if (invoiceRepository.existsByOrderIdAndDeletedFalse(orderId)) {
            throw new ConflictException("An invoice already exists for this order.");
        }
        OrderDetailResponse order = orderServiceClient.getOrder(orderId).block();
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        LocalDate issueDate = LocalDate.now();
        String invoiceNumber = "INV-%d-%06d".formatted(issueDate.getYear(), invoiceRepository.nextInvoiceNumber());

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .subtotal(amount)
                .totalAmount(amount)
                .issueDate(issueDate)
                .build();
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Generated invoice {} for order {}", invoiceNumber, orderId);
        return compose(saved, order);
    }

    public InvoiceResponse findById(UUID id, UserPrincipal principal) {
        Invoice invoice = principal.isAdmin()
                ? invoiceRepository.findByIdAndDeletedFalse(id).orElseThrow(this::notFound)
                : invoiceRepository.findByIdAndCustomerIdAndDeletedFalse(id, principal.userId()).orElseThrow(this::notFound);
        return compose(invoice, null);
    }

    public InvoiceResponse findByOrderId(UUID orderId, UserPrincipal principal) {
        Invoice invoice = principal.isAdmin()
                ? invoiceRepository.findByOrderIdAndDeletedFalse(orderId).orElseThrow(this::notFound)
                : invoiceRepository.findByOrderIdAndCustomerIdAndDeletedFalse(orderId, principal.userId()).orElseThrow(this::notFound);
        return compose(invoice, null);
    }

    public byte[] generatePdf(UUID id, UserPrincipal principal) {
        InvoiceResponse response = findById(id, principal);
        return pdfRenderer.render(response);
    }

    public Page<InvoiceSummaryResponse> search(String keyword, UUID customerId, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Specification<Invoice> spec = Specification.where(InvoiceSpecifications.notDeleted());
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(InvoiceSpecifications.hasKeyword(keyword));
        }
        if (customerId != null) {
            spec = spec.and(InvoiceSpecifications.hasCustomerId(customerId));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(InvoiceSpecifications.issuedBetween(dateFrom, dateTo));
        }
        return invoiceRepository.findAll(spec, pageable).map(this::composeSummary);
    }

    public Page<InvoiceSummaryResponse> findMine(UUID customerId, Pageable pageable) {
        return invoiceRepository.findAllByCustomerIdAndDeletedFalse(customerId, pageable).map(this::composeSummary);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Invoice not found or you do not have access to it.");
    }

    /** Runs the order/payment/customer/address read-throughs concurrently rather than
     *  sequentially - all four only need the ids already on the Invoice row itself, none depend
     *  on each other's result. orderHint avoids a redundant re-fetch right after generate()
     *  already fetched the same order. */
    private InvoiceResponse compose(Invoice invoice, OrderDetailResponse orderHint) {
        Mono<OrderDetailResponse> orderMono = orderHint != null
                ? Mono.just(orderHint)
                : orderServiceClient.getOrder(invoice.getOrderId()).onErrorResume(ex -> Mono.empty());
        Mono<PaymentDetailResponse> paymentMono = paymentServiceClient.getPrimaryPaymentForOrder(invoice.getOrderId());
        Mono<CustomerDetailResponse> customerMono = customerServiceClient.getCustomer(invoice.getCustomerId())
                .onErrorResume(ex -> Mono.empty());
        Mono<AddressDetailResponse> addressMono = customerServiceClient.getDefaultAddress(invoice.getCustomerId());

        Tuple4<OrderDetailResponse, PaymentDetailResponse, CustomerDetailResponse, AddressDetailResponse> result =
                Mono.zip(
                        orderMono.defaultIfEmpty(new OrderDetailResponse()),
                        paymentMono.defaultIfEmpty(new PaymentDetailResponse()),
                        customerMono.defaultIfEmpty(new CustomerDetailResponse()),
                        addressMono.defaultIfEmpty(new AddressDetailResponse())
                ).block();

        OrderDetailResponse order = result.getT1();
        PaymentDetailResponse payment = result.getT2();
        CustomerDetailResponse customer = result.getT3();
        AddressDetailResponse address = result.getT4();

        List<InvoiceResponse.LineItem> items = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(i -> InvoiceResponse.LineItem.builder()
                        .milkType(i.getMilkType()).quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice()).totalPrice(i.getTotalPrice())
                        .build())
                .toList();

        String customerName = customer.getFirstName() != null
                ? (customer.getFirstName() + " " + (customer.getLastName() != null ? customer.getLastName() : "")).trim()
                : null;

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .issueDate(invoice.getIssueDate())
                .orderId(invoice.getOrderId())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .orderStatus(order.getStatus())
                .items(items)
                .customerId(invoice.getCustomerId())
                .customerName(customerName)
                .customerMobile(customer.getMobile())
                .customerEmail(customer.getEmail())
                .billingAddress(address.getAddressLine1() == null ? null : InvoiceResponse.Address.builder()
                        .addressLine1(address.getAddressLine1()).addressLine2(address.getAddressLine2())
                        .city(address.getCity()).state(address.getState()).pincode(address.getPincode())
                        .build())
                .subtotal(invoice.getSubtotal())
                .totalAmount(invoice.getTotalAmount())
                .payment(payment.getPaymentStatus() == null ? null : InvoiceResponse.PaymentInfo.builder()
                        .paymentReference(payment.getPaymentReference()).paymentMethod(payment.getPaymentMethod())
                        .paymentStatus(payment.getPaymentStatus()).paidAt(payment.getPaidAt())
                        .build())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private InvoiceSummaryResponse composeSummary(Invoice invoice) {
        Tuple2<OrderDetailResponse, CustomerDetailResponse> result = Mono.zip(
                orderServiceClient.getOrder(invoice.getOrderId()).onErrorResume(ex -> Mono.empty()).defaultIfEmpty(new OrderDetailResponse()),
                customerServiceClient.getCustomer(invoice.getCustomerId()).onErrorResume(ex -> Mono.empty()).defaultIfEmpty(new CustomerDetailResponse())
        ).block();

        OrderDetailResponse order = result.getT1();
        CustomerDetailResponse customer = result.getT2();
        String customerName = customer.getFirstName() != null
                ? (customer.getFirstName() + " " + (customer.getLastName() != null ? customer.getLastName() : "")).trim()
                : null;

        return InvoiceSummaryResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .issueDate(invoice.getIssueDate())
                .orderId(invoice.getOrderId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .customerId(invoice.getCustomerId())
                .customerName(customerName)
                .totalAmount(invoice.getTotalAmount())
                .build();
    }
}
