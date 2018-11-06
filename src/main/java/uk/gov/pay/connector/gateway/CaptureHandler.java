package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

public interface CaptureHandler {

    public GatewayResponse<BaseResponse> capture(CaptureGatewayRequest request);

}
