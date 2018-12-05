package uk.gov.pay.connector.gateway;

import fj.data.Either;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

public interface CaptureHandler {

    CaptureResponse capture(CaptureGatewayRequest request);

    default CaptureResponse fromUnmarshalled(Either<GatewayError, ? extends BaseCaptureResponse> unmarshalled, 
                                             PaymentGatewayName paymentGatewayName) {
        if (unmarshalled.isLeft())
            return CaptureResponse.fromGatewayError(unmarshalled.left().value());

        BaseCaptureResponse captureResponse = unmarshalled.right().value();
        if (isNotBlank(captureResponse.getErrorCode())) {
            GatewayError gatewayError = genericGatewayError(format("%s capture response (error code: %s, error: %s)", 
                    paymentGatewayName.getName(), captureResponse.getErrorCode(), captureResponse.getErrorMessage()));
            return CaptureResponse.fromGatewayError(gatewayError);
        }

        return CaptureResponse.fromTransactionId(unmarshalled.right().value().getTransactionId(), PENDING);
    }
}
