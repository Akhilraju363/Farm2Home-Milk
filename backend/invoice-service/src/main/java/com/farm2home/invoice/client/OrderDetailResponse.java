package com.farm2home.invoice.client;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Minimal local projection of order-service's real OrderResponse - only the fields an invoice
 *  actually renders (invoice number/subtotal generation and the line-item table). Jackson ignores
 *  the rest of the real response body, so this stays independent of order-service's full DTO. */
@Data
public class OrderDetailResponse {
    private UUID id;
    private String orderNumber;
    private UUID customerId;
    private LocalDate orderDate;
    private String status;
    private BigDecimal totalAmount;
    private List<Item> items;

    @Data
    public static class Item {
        private String milkType;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }
}
