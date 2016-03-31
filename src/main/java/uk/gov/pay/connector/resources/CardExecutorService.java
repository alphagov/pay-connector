package uk.gov.pay.connector.resources;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public class CardExecutorService<T> {

    public Future<T> execute(Supplier<T> callable) {
        Callable<T> task = () -> callable.get();
        ExecutorService executor = Executors.newCachedThreadPool();
        return executor.submit(task);
    }
}
