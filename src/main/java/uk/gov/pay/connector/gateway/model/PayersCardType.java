package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;

/**
 * The enum type that should be used to map from and to JSON when
 * dealing with what type (CREDIT, DEBIT, CREDIT_OR_DEBIT) card is
 * used to make a payment. This is also used to calculate corporate
 * surcharges, based on other rules.
 * <p>
 * This should not be confused with {@link CardTypeEntity.SupportedType}
 * which is used to map values from the database to frontend
 * labels. This is used to drive the frontend UI
 */
public enum PayersCardType {
    DEBIT,
    CREDIT,
    CREDIT_OR_DEBIT
}
