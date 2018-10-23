package uk.gov.pay.connector.paymentprocessor.service;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

interface TransactionalGatewayOperation<T extends BaseResponse> {

    ChargeEntity preOperation(String chargeId);

    GatewayResponse<T> operation(ChargeEntity chargeEntity);

    GatewayResponse<T> postOperation(String chargeId, GatewayResponse<T> operationResponse);
}
