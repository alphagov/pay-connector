package uk.gov.pay.connector.service;

import com.google.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CardExecutorServiceConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import javax.ws.rs.WebApplicationException;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static java.lang.Runtime.getRuntime;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.*;

// this service runs the supplied function in a new Thread
public class CardExecutorService<T> {

    private static final Logger logger = LoggerFactory.getLogger(CardExecutorService.class);

    private CardExecutorServiceConfig config;
    private ExecutorService executor;

    public enum ExecutionStatus {
        COMPLETED,
        FAILED,
        IN_PROGRESS
    }

    @Inject
    public CardExecutorService(ConnectorConfiguration configuration) {
        this.config = configuration.getExecutorServiceConfig();
        this.executor = Executors.newFixedThreadPool(config.getThreadsPerCpu() * getRuntime().availableProcessors());
        addShutdownHook();
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                String className = CardExecutorService.class.getSimpleName();
                logger.info("Shutting down " + className);
                executor.shutdown();
                logger.info("Awaiting for " + className + " threads to terminate");
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.error("Error while waiting for " + className + " threads to terminate");
                }
                executor.shutdownNow();
            }
        });
    }

    // accepts a supplier function and executed that in a separate Thread of its own.
    // returns a Pair of the execution status and the return type
    public Pair<ExecutionStatus, T> execute(Supplier<T> callable) {
        Callable<T> task = () -> callable.get();
        Future<T> futureObject = executor.submit(task);

        try {
            return Pair.of(COMPLETED, futureObject.get(config.getTimeoutInSeconds(), TimeUnit.SECONDS));
        } catch (ExecutionException executionException) {
            if (executionException.getCause() instanceof WebApplicationException) {
                throw (WebApplicationException) executionException.getCause();
            }
            return Pair.of(FAILED, null);
        } catch (InterruptedException interruptedException) {
            return Pair.of(FAILED, null);
        } catch (TimeoutException timeoutException) {
            return Pair.of(IN_PROGRESS, null);
        }
    }
}
