package uk.gov.pay.connector.gateway.worldpay;

import java.util.Objects;

public record WorldpayExemptionResponse(String result, String reason) {

    public WorldpayExemptionResponse {
        Objects.requireNonNull(result);
    }

}
