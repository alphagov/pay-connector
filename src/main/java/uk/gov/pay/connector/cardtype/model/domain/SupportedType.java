package uk.gov.pay.connector.cardtype.model.domain;

/**
 * Internal entity used to drive the frontend UI based on the
 * strings stored in the table {@code card_types}. This represents
 * which card types are supported by the service, not what card types
 * are used for payment by the paying user
 * <p>
 * This should be used only for driving the UI
 */
public enum SupportedType {
    CREDIT,
    DEBIT
}
