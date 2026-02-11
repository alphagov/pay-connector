package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface WorldpayCardNumberAuthoriseRequest extends WorldpayAuthoriseRequest {

    String cardNumber();
    String expiryMonthTwoDigits();
    String expiryYearFourDigits();
    String cardholderName();
    String cvc();

}
