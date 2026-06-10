package uk.gov.pay.connector.gateway.model.request.records;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record WorldpayMotoAuthoriseRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        String description,
        String amountInPence,
        String cardNumber,
        String expiryDateMonthTwoDigits,
        String expiryDateYearFourDigits,
        String cardholderName,
        String cvc
) implements WorldpayRequest {
    @Override
    public String toString(){
        return getClass().getSimpleName() + '[' 
                + "merchantCode= " + merchantCode 
                + ", orderCode= " + orderCode + ']';
    } 
}
