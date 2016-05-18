package uk.gov.pay.connector.service.transaction;

import com.google.inject.persist.Transactional;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class TransactionFlow<T, U, V> {

    private Supplier<T> before;
    private Function<T, U> operation;
    private BiFunction<T, U, V> after;

    public TransactionFlow<T, U, V> startInTx(Supplier<T> preOperation) {
        this.before = preOperation;
        return this;
    }

    public TransactionFlow<T, U, V> operationNotInTx(Function<T, U> operation) {
        this.operation = operation;
        return this;
    }

    public TransactionFlow<T, U, V> completeInTx(BiFunction<T, U, V> postOperation) {
        this.after = postOperation;
        return this;
    }

    public Optional<V> execute() {
        if (before != null) {
            T data = doBefore(before);
            if (operation != null) {
                U response = doOperation(data, operation);
                if (after != null) {
                    return Optional.ofNullable(doAfter(data, response, after));
                }
            }
        }
        return Optional.empty();
    }

    @Transactional
    public T doBefore(Supplier<T> before) {
        return before.get();
    }


    public U doOperation(T charge, Function<T, U> operation) {
        return operation.apply(charge);
    }

    @Transactional
    public V doAfter(T data, U gatewayResponse, BiFunction<T, U, V> after) {
        return after.apply(data, gatewayResponse);
    }


}
