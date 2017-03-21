package uk.gov.pay.connector.service;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.*;

@RunWith(MockitoJUnitRunner.class)
public class CaptureProcessSchedulerTest {

    CaptureProcessScheduler captureProcessScheduler;

    @Mock
    CardCaptureProcess cardCaptureProcess;

    @Mock
    Environment environment;

    @Mock
    LifecycleEnvironment lifecycleEnvironment;

    @Mock
    ScheduledExecutorService scheduledExecutorService;

    ScheduledExecutorServiceBuilder scheduledExecutorServiceBuilder;

    @Before
    public void setup() {
        scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class, invocation -> {
            Object mock = invocation.getMock();
            if(invocation.getMethod().getReturnType().isInstance(mock)) {
                return mock;
            } else {
                return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        when(scheduledExecutorServiceBuilder.build()).thenReturn(scheduledExecutorService);
        when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
        when(lifecycleEnvironment.scheduledExecutorService(anyString())).thenReturn(scheduledExecutorServiceBuilder);

        captureProcessScheduler = new CaptureProcessScheduler(environment, cardCaptureProcess);
    }

    @Test
    public void shouldSetupScheduledExecutorService() {
        verify(lifecycleEnvironment).scheduledExecutorService(CAPTURE_PROCESS_SCHEDULER_NAME);
        verify(scheduledExecutorServiceBuilder).threads(SCHEDULER_THREADS);
        verify(scheduledExecutorServiceBuilder).build();
    }

    @Test
    public void shouldScheduleCaptureProcess() {
        captureProcessScheduler.start();

        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);

        verify(scheduledExecutorService).scheduleAtFixedRate(any(Runnable.class), eq(INITIAL_DELAY_IN_SECONDS), delay.capture(), eq(TimeUnit.SECONDS));
        assertThat(delay.getValue(), both(greaterThanOrEqualTo(RANDOM_INTERVAL_MINIMUM_IN_SECONDS)).and(lessThan(RANDOM_INTERVAL_MAXIMUM_IN_SECONDS)));
    }

    @Test
    public void shouldShutdownScheduledExecutorServiceWhenStopped() {
        captureProcessScheduler.stop();

        verify(scheduledExecutorService).shutdown();
    }
}
