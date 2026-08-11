package com.farm2home.invoice.client;

import com.farm2home.common.core.constants.HeaderConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import org.springframework.http.HttpHeaders;

/**
 * Shared by every cross-service client in this package. invoice-service composes its response
 * from order-service/payment-service/customer-service data it does not own, but the caller who
 * triggered the request (a FARM_MANAGER generating an invoice, or a CUSTOMER/admin reading one)
 * is not necessarily authorized to read the underlying order/payment/customer record under THAT
 * service's own ownership rules - most notably, customer-service's GET /{id} and GET /{id}/addresses
 * only allow SUPER_ADMIN/DELIVERY_MANAGER (not FARM_MANAGER) beyond self-access, which would break
 * invoice generation for the FARM_MANAGER role this feature is explicitly built for.
 *
 * invoice-service's own controller/service layer is the sole authorization gate for every invoice
 * endpoint (generate = FARM_MANAGER/SUPER_ADMIN only; read = ownership-scoped by the customerId
 * already stored on the Invoice). Once that decision is made, downstream reads are "fetch the data
 * this already-authorized request needs," not "re-derive whether this is allowed" - so, like
 * notification-service's CustomerServiceClient (a Kafka-consumer-thread caller with the same
 * "no real per-request identity to forward" shape), these calls present a fixed system identity
 * with SUPER_ADMIN standing rather than forwarding the original caller's possibly-narrower role.
 */
final class SystemIdentityHeaders {

    private static final String SYSTEM_CALLER_ID = "00000000-0000-0000-0000-000000000000";
    private static final String SYSTEM_CALLER_MOBILE = "invoice-service";

    private SystemIdentityHeaders() {
    }

    static void apply(HttpHeaders headers) {
        headers.add(HeaderConstants.X_USER_ID, SYSTEM_CALLER_ID);
        headers.add(HeaderConstants.X_USER_MOBILE, SYSTEM_CALLER_MOBILE);
        headers.add(HeaderConstants.X_USER_ROLES, SecurityConstants.ROLE_SUPER_ADMIN);
    }
}
