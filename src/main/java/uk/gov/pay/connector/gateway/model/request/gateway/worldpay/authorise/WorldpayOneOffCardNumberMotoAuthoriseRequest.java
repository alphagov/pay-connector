package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record WorldpayOneOffCardNumberMotoAuthoriseRequest(
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
        @Nullable String email
        ) implements WorldpayCardNumberAuthoriseRequest, WorldpayOneOffAuthoriseRequest {

    @Override
    public String toString() {
        return getClass().getSimpleName() + '['
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode + ']';
    }

}
