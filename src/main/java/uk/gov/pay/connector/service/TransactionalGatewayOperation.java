package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.OptimisticLockException;

import static fj.data.Either.right;
import static java.lang.String.format;

interface TransactionalGatewayOperation {

    default Either<ErrorResponse, GatewayResponse> executeGatewayOperationFor(ChargeEntity chargeEntity) {
        Either<ErrorResponse, ChargeEntity> preOperationResponse;
        try {
            preOperationResponse = preOperation(chargeEntity);
        } catch (OptimisticLockException e) {
            throw new ConflictRuntimeException(format("Operation for charge conflicting, %s", chargeEntity.getExternalId()));
        }

        Either<ErrorResponse, GatewayResponse> operationResponse = operation(preOperationResponse.right().value());

        Either<ErrorResponse, GatewayResponse> postOperationResponse = postOperation(preOperationResponse.right().value(), operationResponse.right().value());

        return right(postOperationResponse.right().value());
    }

    Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity);

    Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity);

    Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse);
}
