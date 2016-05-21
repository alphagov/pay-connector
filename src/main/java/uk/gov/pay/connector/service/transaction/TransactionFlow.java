package uk.gov.pay.connector.service.transaction;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.exception.ConflictRuntimeException;

import javax.persistence.OptimisticLockException;
import java.util.Objects;

/**
 * <p>Represents and manages a set of transactional (and non-transactional) operations
 * in a provided flow in that respective order.
 * The operations could either be {@link TransactionalOperation} or {@link NonTransactionalOperation}
 * </p>
 * <p>
 * <b>example usage:</b>
 * <pre>
 *  {@code
 *
 *  @Inject
 *  private final Provider<TransactionFlow> transactionFlowProvider ;
 *
 *  GatewayResponse result = transactionFlowProvider.get()
 *   .executeNext((TransactionalOperation<TransactionContext, ChargeEntity>) context-> {
 *       //do some transactional stuff
 *       return chargeEntity;
 *   })
 *   .executeNext((NonTransactionalOperation<TransactionContext, GatewayResponse>) context-> {
 *       //do some non transactional stuff
 *       return gatewayResponse;
 *   })
 *   .complete()
 *   .get(GatewayResponse.class);
 *
 * }
 * </pre>
 */
public final class TransactionFlow {

    private TransactionContext context;

    public TransactionFlow() {
        context = new TransactionContext();
    }

    TransactionFlow(TransactionContext context) {
        this.context = context;
    }

    /**
     * executes the given block of code in a Transactional boundary.
     *
     * @param op  block of code to be executed
     * @param <R> result to be persisted
     * @return
     */
    @Transactional
    public <R> TransactionFlow executeNext(TransactionalOperation<TransactionContext, R> op) {
        Objects.requireNonNull(op);
        R result = op.execute(context);
        context.put(result);
        return this;
    }

    /**
     * executes the given block of code in a Transactional boundary and handles Optimistic Lock errors
     * @throws ConflictRuntimeException - in case of a version clash
     * @param op
     * @param <R>
     * @return
     */
    @Transactional
    public <R> TransactionFlow executeNext(PreTransactionalOperation<TransactionContext, R> op) {
        Objects.requireNonNull(op);
        try {
            R result = op.execute(context);
            context.put(result);
            return this;
        } catch (OptimisticLockException e) {
            throw new ConflictRuntimeException(e);
        }
    }

    /**
     * executes the given block of code outside of a Transactional boundary.
     *
     * @param op  block of code to be executed
     * @param <R> result to be persisted
     * @return
     */
    public <R> TransactionFlow executeNext(NonTransactionalOperation<TransactionContext, R> op) {
        Objects.requireNonNull(op);
        R result = op.execute(context);
        context.put(result);
        return this;
    }

    /**
     * demarcates the end of transaction flow
     *
     * @return all result objects persisted during the execution of transaction flow.
     */
    public TransactionContext complete() {
        return context;
    }
}
