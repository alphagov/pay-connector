package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Locale;

public enum StripeTransferMetadataReason {
    NOT_DEFINED,
    TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT,
    TRANSFER_REFUND_AMOUNT,
    TRANSFER_PAYMENT_AMOUNT;
    
    public static StripeTransferMetadataReason fromString(String value) {
        if (value == null) {
            return NOT_DEFINED;
        }
        return Arrays.stream(StripeTransferMetadataReason.values())
                .filter(stripeTransferMetadataReason -> StringUtils.equals(stripeTransferMetadataReason.name(), value.toUpperCase(Locale.ENGLISH)))
                .findFirst()
                .orElse(StripeTransferMetadataReason.NOT_DEFINED);
    }
}
