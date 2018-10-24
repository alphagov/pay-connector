package uk.gov.pay.connector.charge.service.transaction;

/**
 * Represents a block of code that should be executed outside of a transaction boundary
 * @param <TransactionContext>
 * @param <R> return value
 */
@FunctionalInterface
public interface NonTransactionalOperation<TransactionContext, R> extends ManagedOperation<TransactionContext, R> {

}
