package uk.gov.pay.connector.service;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.CAPTURE_PROCESS_SCHEDULER_NAME;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.SCHEDULER_THREADS;

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

        verify(scheduledExecutorService).scheduleAtFixedRate(any(Runnable.class), eq(1L), eq(2L), eq(TimeUnit.MINUTES));
    }

    @Test
    public void shouldShutdownScheduledExecutorServiceWhenStopped() {
        captureProcessScheduler.stop();

        verify(scheduledExecutorService).shutdown();
    }
}
