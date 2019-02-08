package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;

public interface RefundHandler {

    GatewayRefundResponse refund(RefundGatewayRequest request) throws GatewayErrorException;
}
