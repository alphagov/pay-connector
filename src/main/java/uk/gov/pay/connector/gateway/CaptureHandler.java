package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

public interface CaptureHandler {

    CaptureResponse capture(CaptureGatewayRequest request);
}
