package uk.gov.pay.connector.gateway.stripe.request;

import java.util.Arrays;
import java.util.Locale;

public enum StripeTransferMetadataReason {
    NOT_DEFINED,
    UNRECOGNISED,
    TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT,
    TRANSFER_REFUND_AMOUNT,
    TRANSFER_PAYMENT_AMOUNT,
    TRANSFER_DISPUTE_AMOUNT;

    @Override
    public String toString() {
        return name().toLowerCase((Locale.ENGLISH));
    }

    public static StripeTransferMetadataReason fromString(String value) {
        if (value == null) {
            return NOT_DEFINED;
        }
        return Arrays.stream(StripeTransferMetadataReason.values())
                .filter(stripeTransferMetadataReason -> stripeTransferMetadataReason.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(UNRECOGNISED);
    }
}
