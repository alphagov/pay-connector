package uk.gov.pay.connector.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

class TimedThreadPoolExecutorTest {
    
    final ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .build();
    TimedThreadPoolExecutor underTest;

    @BeforeEach
    void setUp() {
        underTest = new TimedThreadPoolExecutor(10, threadFactory, 3, TimeUnit.SECONDS);
    }
    
    @Test
    void timedThreadPoolExecutor_shouldSubmitAndProcessTasks() throws InterruptedException, ExecutionException {
        var future = underTest.submit(() -> "done");
        assertThat(future.get(), is("done"));
        await().untilAsserted(() -> assertThat(underTest.getCompletedTaskCount(), is(1L)));
    }
    
    @Test
    void timedThreadPoolExecutor_shouldScheduleTimeoutTasks() {
        addNTasksToQueue(5);
        await().untilAsserted(() -> assertThat(underTest.getTaskCount(), is(5L)));
        await().untilAsserted(() -> assertThat(underTest.getTimeoutTasks().size(), is(5)));
    }
    
    @Test
    void timedThreadPoolExecutor_shouldInterruptLongRunningTask() {
        assertThrows(ExecutionException.class, () -> {
            var future = underTest.submit(() -> {
                try {
                    Thread.sleep(100000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            future.get();
        });
    }
    
    @Test
    void timedThreadPoolExecutor_shouldQueueTasks_whenCorePoolBusy() {
        addNTasksToQueue(15);
        await().untilAsserted(() -> assertThat(underTest.getActiveCount(), is(10)));
        await().untilAsserted(() -> assertThat(underTest.getQueue().size(), is(5)));
    }
    
    private void addNTasksToQueue(int n) {
        for (int i = 0; i < n; i++) {
            underTest.submit(() -> {
                try {
                    Thread.sleep(20000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

}
