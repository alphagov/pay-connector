package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.capture;

import org.jspecify.annotations.NullMarked;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.WorldpayRequest;

@NullMarked
public record WorldpayCaptureRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        long amountInPence,
        String dayTwoDigits,
        String monthTwoDigits,
        String yearFourDigits
        ) implements WorldpayRequest {

    @Override
    public String toString() {
        return "WorldpayCaptureRequest["
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode + ']';
    }

}
