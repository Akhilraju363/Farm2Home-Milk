package com.farm2home.customer.domain.enums;

/** Maps to the DPDP Act 2023 data-principal rights: s.11 (access), s.12 (correction/erasure),
 *  s.6(4)+s.13 (withdraw consent as easily as it was given), plus a general grievance channel
 *  (s.13's grievance-redressal obligation) and a catch-all. */
public enum DataRightsRequestType {
    ACCESS,
    CORRECTION,
    ERASURE,
    WITHDRAW_CONSENT,
    GRIEVANCE,
    OTHER
}
