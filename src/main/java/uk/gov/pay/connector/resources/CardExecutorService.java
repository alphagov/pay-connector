package uk.gov.pay.connector.resources;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.resources.CardExecutorService.ExecutionStatus.*;

// this service runs the supplied function in a new Thread
public class CardExecutorService<T> {

    private static final Logger logger = LoggerFactory.getLogger(CardExecutorService.class);
    private static final String TIMEOUT_ENV_VAR = "AUTH_READ_TIMEOUT";
    private static int timeout = 10;
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static ConcurrentMap<String, Future> futureObjects = new ConcurrentHashMap<>();

    public enum ExecutionStatus {
        COMPLETED,
        FAILED,
        IN_PROGRESS
    }

    public CardExecutorService() {
        if (isNotBlank( System.getProperty(TIMEOUT_ENV_VAR))) {
            timeout = Integer.valueOf(System.getProperty(TIMEOUT_ENV_VAR));
        }
    }

    // accepts a supplier function and executed that in a separate Thread of its own.
    // returns a Pair of the execution status and the return type
    public Pair<ExecutionStatus, T> execute(String id, Supplier<T> callable) {
        Callable<T> task = () -> callable.get();
        Future<T> futureObject = executor.submit(task);

        try {
            return Pair.of(COMPLETED, futureObject.get(timeout, TimeUnit.SECONDS));
        } catch (InterruptedException | ExecutionException e) {
            return Pair.of(FAILED, null);
        } catch (TimeoutException e) {
            futureObjects.putIfAbsent(id, futureObject);
            return Pair.of(IN_PROGRESS, null);
        }
    }
}
