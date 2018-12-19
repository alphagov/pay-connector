package uk.gov.pay.connector.gateway;

import fj.data.Either;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;

public interface RefundHandler {

    GatewayRefundResponse refund(RefundGatewayRequest request);

    default GatewayRefundResponse fromUnmarshalled(Either<GatewayError, ? extends BaseRefundResponse> unmarshalled, GatewayRefundResponse.RefundState refundState) {
        if (unmarshalled.isLeft())
            return GatewayRefundResponse.fromGatewayError(unmarshalled.left().value());

        return GatewayRefundResponse.fromBaseRefundResponse(unmarshalled.right().value(), refundState);
    }
}
