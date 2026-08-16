package com.farm2home.customer.domain.enums;

/** Each purpose is recorded/withdrawn independently (DPDP Act 2023 s.6 - consent must be granular,
 *  per-purpose, and as easy to withdraw as to give). ESSENTIAL_SERVICE covers processing that is
 *  strictly necessary to provide the service the customer signed up for (account, order
 *  fulfillment, delivery) - DPDP does not require "consent" for this (it can rest on the
 *  contract/legitimate-use basis), but the checkbox is still shown and recorded as an
 *  acknowledgment of the Privacy Notice, not a true opt-in gate. The other three are genuine
 *  opt-in, unticked-by-default choices. */
public enum ConsentPurpose {
    ESSENTIAL_SERVICE,
    MARKETING_COMMUNICATIONS,
    LOCATION_TRACKING,
    ANALYTICS_COOKIES
}
