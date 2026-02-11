package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record WorldpayRecurringMerchantInitiatedAuthoriseRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        String description,
        String amountInPence,
        String paymentTokenId,
        String authenticatedShopperId,
        @Nullable String schemeTransactionIdentifier,
        @Nullable String merchantInitiatedReason
        ) implements WorldpayAuthoriseRequest {

    @Override
    public String toString() {
         return getClass().getSimpleName() + '['
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode + ']';
    }

}
