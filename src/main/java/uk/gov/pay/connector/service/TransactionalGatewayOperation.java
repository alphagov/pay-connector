package uk.gov.pay.connector.service;

import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.persistence.OptimisticLockException;

interface TransactionalGatewayOperation<T extends BaseResponse> {

    default GatewayResponse<T> executeGatewayOperationFor(String chargeId) {
        ChargeEntity charge;
        try {
            charge = preOperation(chargeId);
        } catch (OptimisticLockException e) {
            throw new ConflictRuntimeException(chargeId);
        }
        GatewayResponse<T> operationResponse = operation(charge);
        return postOperation(chargeId, operationResponse);
    }

    ChargeEntity preOperation(String chargeId);

    GatewayResponse<T> operation(ChargeEntity chargeEntity);

    GatewayResponse<T> postOperation(String chargeId, GatewayResponse<T> operationResponse);
}
