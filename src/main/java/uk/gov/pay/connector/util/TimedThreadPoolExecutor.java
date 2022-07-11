package uk.gov.pay.connector.util;

import org.eclipse.jetty.util.BlockingArrayQueue;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TimedThreadPoolExecutor extends ThreadPoolExecutor {
    private final long timeout;
    private final TimeUnit timeoutUnit;
    // use a separate executor to handle the timeout tasks
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentMap<Runnable, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    public TimedThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, long timeout, TimeUnit timeoutUnit) {
        super(corePoolSize, Integer.MAX_VALUE, 10L, TimeUnit.MILLISECONDS, new BlockingArrayQueue<>(), threadFactory);
        if (timeout <= 0) throw new IllegalArgumentException("Timeout must be a positive value");
        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
    }

    @Override
    public void shutdown() {
        timeoutExecutor.shutdown();
        super.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        timeoutExecutor.shutdownNow();
        return super.shutdownNow();
    }

    /***
     * pre execution hook that schedules a timeout task at the point the task starts executing
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        final ScheduledFuture<?> scheduled = timeoutExecutor.schedule(new TimedTask(t), timeout, timeoutUnit);
        timeoutTasks.put(r, scheduled);
    }

    /***
     * post execution hook that removes a timeout task if the work completed within the timeout window
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        ScheduledFuture<?> timeoutTask = timeoutTasks.remove(r);
        if(timeoutTask != null) {
            timeoutTask.cancel(false);
        }
    }
    
    protected ConcurrentMap<Runnable, ScheduledFuture<?>> getTimeoutTasks() {
        return timeoutTasks;
    }
    
    static class TimedTask implements Runnable {
        private final Thread thread;
        
        public TimedTask(Thread threadToInterrupt) {
            this.thread = threadToInterrupt;
        }

        @Override
        public void run() {
            thread.interrupt();
        }
    }
}
