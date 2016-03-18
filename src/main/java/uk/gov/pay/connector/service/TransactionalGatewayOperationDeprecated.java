package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.OptimisticLockException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.conflictError;

public class TransactionalGatewayOperationDeprecated<R> {

    private Function<ChargeEntity, Either<GatewayError, ChargeEntity>> preOperationCallback;
    private Function<ChargeEntity, Either<GatewayError, R>> operationCallback;
    private BiFunction<ChargeEntity, R, Either<GatewayError, R>> postOperationCallback;

    private TransactionalGatewayOperationDeprecated(Function<ChargeEntity, Either<GatewayError, ChargeEntity>> preOperationCallback,
                                                    Function<ChargeEntity, Either<GatewayError, R>> operationCallback,
                                                    BiFunction<ChargeEntity, R, Either<GatewayError, R>> postOperationCallback) {
        this.preOperationCallback = preOperationCallback;
        this.operationCallback = operationCallback;
        this.postOperationCallback = postOperationCallback;
    }

    static public GatewayOperationBuilder newBuilder() {

        return new GatewayOperationBuilder();
    }

    public Either<GatewayError, R> executeFor(ChargeEntity chargeEntity) {
        Either<GatewayError, ChargeEntity> preOperationResponse;
        try {
            preOperationResponse = preOperation(chargeEntity);
        } catch (OptimisticLockException e) {
            return left(conflictError(format("Operation for charge conflicting, %s", chargeEntity.getId())));
        }

        if (preOperationResponse.isLeft())
            return left(preOperationResponse.left().value());

        Either<GatewayError, R> operationResponse = operation(preOperationResponse);
        if (operationResponse.isLeft())
            return left(operationResponse.left().value());

        Either<GatewayError, R> postOperationResponse = postOperation(preOperationResponse.right().value(), operationResponse.right().value());
        if (postOperationResponse.isLeft())
            return left(postOperationResponse.left().value());

        return right(postOperationResponse.right().value());
    }

    @Transactional
    private Either<GatewayError, ChargeEntity> preOperation(ChargeEntity chargeEntity) {
        return preOperationCallback.apply(chargeEntity);
    }

    protected Either<GatewayError, R> operation(Either<GatewayError, ChargeEntity> preOperationResponse) {
        return operationCallback.apply((preOperationResponse.right().value()));
    }

    @Transactional
    protected Either<GatewayError, R> postOperation(ChargeEntity chargeEntity, R response) {
        return postOperationCallback.apply(chargeEntity, response);
    }

    public static class GatewayOperationBuilder<T> {
        private Function<ChargeEntity, Either<GatewayError, ChargeEntity>> preOperation;
        private Function<ChargeEntity, Either<GatewayError, T>> operation;
        private BiFunction<ChargeEntity, T, Either<GatewayError, T>> postOperation;

        public TransactionalGatewayOperationDeprecated<T> build() {
            return new TransactionalGatewayOperationDeprecated(preOperation, operation, postOperation);
        }

        public GatewayOperationBuilder<T> withPreOperation(Function<ChargeEntity, Either<GatewayError, ChargeEntity>> preOperation) {
            this.preOperation = preOperation;
            return this;
        }

        public GatewayOperationBuilder<T> withOperation(Function<ChargeEntity, Either<GatewayError, T>> operation) {
            this.operation = operation;
            return this;
        }

        public GatewayOperationBuilder<T> withPostOperation(BiFunction<ChargeEntity, T, Either<GatewayError, T>> postOperation) {
            this.postOperation = postOperation;
            return this;
        }

    }

}
