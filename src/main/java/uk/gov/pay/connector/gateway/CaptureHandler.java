package uk.gov.pay.connector.gateway;

import fj.data.Either;
import uk.gov.pay.connector.gateway.CaptureResponse.ChargeState;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import static java.lang.String.format;

public interface CaptureHandler {

    CaptureResponse capture(CaptureGatewayRequest request);

    default CaptureResponse fromUnmarshalled(Either<GatewayError, ? extends BaseCaptureResponse> unmarshalled, ChargeState chargeState) {
        if (unmarshalled.isLeft())
            return CaptureResponse.fromGatewayError(unmarshalled.left().value());

        return CaptureResponse.fromBaseCaptureResponse(unmarshalled.right().value(), chargeState);
    }
}
