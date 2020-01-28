package uk.gov.pay.connector.refund.service;

import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

public class ChargeRefundResponse {

    private GatewayRefundResponse gatewayRefundResponse;
    private RefundEntity refundEntity;

    public ChargeRefundResponse(GatewayRefundResponse gatewayRefundResponse, RefundEntity refundEntity) {
        this.gatewayRefundResponse = gatewayRefundResponse;
        this.refundEntity = refundEntity;
    }

    public GatewayRefundResponse getGatewayRefundResponse() {
        return gatewayRefundResponse;
    }

    public RefundEntity getRefundEntity() {
        return refundEntity;
    }
}
