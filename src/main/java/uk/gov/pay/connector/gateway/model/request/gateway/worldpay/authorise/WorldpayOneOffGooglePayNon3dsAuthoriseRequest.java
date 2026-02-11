package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record WorldpayOneOffGooglePayNon3dsAuthoriseRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        String description,
        String amountInPence,
        String protocolVersion,
        String signature,
        String signedMessage,
        @Nullable String email
        ) implements WorldpayGooglePayAuthoriseRequest, WorldpayOneOffAuthoriseRequest {

    @Override
    public String toString() {
        return getClass().getSimpleName() + '['
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode + ']';
    }

}
