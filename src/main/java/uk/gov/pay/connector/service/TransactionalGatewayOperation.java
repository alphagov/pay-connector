package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

interface TransactionalGatewayOperation<T extends BaseResponse> {

    ChargeEntity preOperation(String chargeId);

    GatewayResponse<T> operation(ChargeEntity chargeEntity);

    GatewayResponse<T> postOperation(String chargeId, GatewayResponse<T> operationResponse);
}
