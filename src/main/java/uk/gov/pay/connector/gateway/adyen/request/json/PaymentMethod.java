package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentMethod(
        @JsonProperty("cvc")
        String cvc,
        @JsonProperty("expiryMonth")
        String expiryMonth,
        @JsonProperty("expiryYear")
        String expiryYear,
        @JsonProperty("holderName")
        String holderName,
        @JsonProperty("number")
        String number,
        @JsonProperty("type")
        String type,
        @JsonProperty("storedPaymentMethodId")
        String storedPaymentMethodId
        ) {

    public static PaymentMethod card(String cvc,
                                     String expiryMonth,
                                     String expiryYear,
                                     String holderName,
                                     String number) {
        return new PaymentMethod(cvc, expiryMonth, expiryYear, holderName, number, "scheme", null);
    }

    public static PaymentMethod stored(String storedPaymentMethodId) {
        return new PaymentMethod(null, null, null, null, null, "scheme", storedPaymentMethodId);
    }
}
