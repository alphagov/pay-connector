package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.refund;

import org.jspecify.annotations.NullMarked;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.WorldpayRequest;

@NullMarked
public record WorldpayRefundRequest(
        String username,
        String password,
        String merchantCode,
        String orderCode,
        String refundReference,
        long amountInPence
    ) implements WorldpayRequest {

    @Override
    public String toString() {
        return "WorldpayRefundRequest["
                + "merchantCode=" + merchantCode
                + ", orderCode=" + orderCode
                + ", refundReference=" + refundReference + ']';
    }
}
