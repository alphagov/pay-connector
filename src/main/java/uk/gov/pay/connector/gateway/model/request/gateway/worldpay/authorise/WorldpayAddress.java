package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record WorldpayAddress(
        String address1,
        @Nullable String address2,
        String city,
        @Nullable String state,
        String postalCode,
        String countryCode
        ) {

    @Override
    public String toString() {
        return "WorldpayAddress";
    }

}
    
