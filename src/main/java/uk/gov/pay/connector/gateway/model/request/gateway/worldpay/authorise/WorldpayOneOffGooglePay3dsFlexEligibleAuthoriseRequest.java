package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record WorldpayOneOffGooglePay3dsFlexEligibleAuthoriseRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        String description,
        String amountInPence,
        String protocolVersion,
        String signature,
        String signedMessage,
        @Nullable String email,
        @Nullable String ipAddress,
        String sessionId,
        String acceptHeader,
        String userAgentHeader,
        @Nullable String dfReferenceId
        ) implements WorldpayGooglePayAuthoriseRequest,
        WorldpayOneOffAuthoriseRequest,
        Worldpay3dsFlexEligibleAuthoriseRequest,
        WorldpayAuthoriseRequestWithOptionalIpAddress
{

    @Override
    public String toString() {
        return getClass().getSimpleName() + '['
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode + ']';
    }

}
