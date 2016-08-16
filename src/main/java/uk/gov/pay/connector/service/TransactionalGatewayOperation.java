package uk.gov.pay.connector.service;

import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.persistence.OptimisticLockException;

interface TransactionalGatewayOperation<T extends BaseResponse> {

    default GatewayResponse<T> executeGatewayOperationFor(ChargeEntity chargeEntity) {
        ChargeEntity preOperationResponse;
        try {
            preOperationResponse = preOperation(chargeEntity);
        } catch (OptimisticLockException e) {
            throw new ConflictRuntimeException(chargeEntity.getExternalId());
        }

        GatewayResponse<T> operationResponse = operation(preOperationResponse);

        return postOperation(preOperationResponse, operationResponse);
    }

    ChargeEntity preOperation(ChargeEntity chargeEntity);

    GatewayResponse<T> operation(ChargeEntity chargeEntity);

    GatewayResponse<T> postOperation(ChargeEntity chargeEntity, GatewayResponse<T> operationResponse);
}
