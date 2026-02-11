package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        String description,
        String amountInPence,
        String cardNumber,
        String expiryMonthTwoDigits,
        String expiryYearFourDigits,
        String cardholderName,
        String cvc,
        @Nullable String email,
        @Nullable WorldpayAddress address,
        @Nullable String ipAddress,
        String sessionId,
        String acceptHeader,
        String userAgentHeader,
        @Nullable String browserLanguageTag,
        @Nullable String dfReferenceId,
        @Nullable WorldpayExemptionRequest exemption
        ) implements WorldpayCardNumberAuthoriseRequest,
        WorldpayOneOffAuthoriseRequest,
        Worldpay3dsFlexEligibleAuthoriseRequest,
        WorldpayAuthoriseRequestWithOptional3dsExemption,
        WorldpayAuthoriseRequestWithOptionalBillingAddress,
        WorldpayAuthoriseRequestWithOptionalIpAddress{

    @Override
    public String toString() {
        return getClass().getSimpleName() + '['
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode + ']';
    }

}
