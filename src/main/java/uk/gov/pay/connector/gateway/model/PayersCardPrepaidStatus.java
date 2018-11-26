package uk.gov.pay.connector.gateway.model;

/**
 * The enum type that should be used to map from and to JSON when
 * dealing with prepaid nature of the card (PREPAID, NOT_PREPAID, UNKNOWN)
 * used to make a payment. This is also used to calculate corporate
 * surcharges, based on other rules.
 */
public enum PayersCardPrepaidStatus {
    PREPAID,
    NOT_PREPAID,
    UNKNOWN
}
