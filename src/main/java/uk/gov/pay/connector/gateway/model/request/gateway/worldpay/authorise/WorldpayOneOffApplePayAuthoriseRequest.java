package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record WorldpayOneOffApplePayAuthoriseRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        String description,
        String amountInPence,
        String tokenNumber,
        String cryptogram,
        @Nullable String eciIndicator,
        @Nullable String cardholderName,
        @Nullable String email
        ) implements WorldpayAuthoriseRequest {

    @Override
    public String toString() {
        return getClass().getSimpleName() + '['
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode + ']';
    }

}
