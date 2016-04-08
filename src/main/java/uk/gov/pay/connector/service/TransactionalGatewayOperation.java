package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.OptimisticLockException;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.conflictError;

interface TransactionalGatewayOperation {

    default Either<ErrorResponse, GatewayResponse> executeGatewayOperationFor(ChargeEntity chargeEntity) {
        Either<ErrorResponse, ChargeEntity> preOperationResponse;
        try {
            preOperationResponse = preOperation(chargeEntity);
        } catch (OptimisticLockException e) {
            return left(conflictError(format("Operation for charge conflicting, %s", chargeEntity.getExternalId())));
        }

        if (preOperationResponse.isLeft())
            return left(preOperationResponse.left().value());

        Either<ErrorResponse, GatewayResponse> operationResponse = operation(preOperationResponse.right().value());
        if (operationResponse.isLeft())
            return left(operationResponse.left().value());

        Either<ErrorResponse, GatewayResponse> postOperationResponse = postOperation(preOperationResponse.right().value(), operationResponse.right().value());
        if (postOperationResponse.isLeft())
            return left(postOperationResponse.left().value());

        return right(postOperationResponse.right().value());
    }

    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity);

    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity);

    public Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse);
}
