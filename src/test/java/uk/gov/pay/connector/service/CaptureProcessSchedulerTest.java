package uk.gov.pay.connector.service;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.XrayUtils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.CAPTURE_PROCESS_SCHEDULER_NAME;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.INITIAL_DELAY_IN_SECONDS;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.RANDOM_INTERVAL_MAXIMUM_IN_SECONDS;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.RANDOM_INTERVAL_MINIMUM_IN_SECONDS;
import static uk.gov.pay.connector.service.CaptureProcessScheduler.SCHEDULER_THREADS;

@RunWith(MockitoJUnitRunner.class)
public class CaptureProcessSchedulerTest {

    @Mock
    private CardCaptureProcess cardCaptureProcess;

    @Mock
    private Environment environment;

    @Mock
    private LifecycleEnvironment lifecycleEnvironment;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    private ScheduledExecutorServiceBuilder scheduledExecutorServiceBuilder;

    private XrayUtils xrayUtils = new XrayUtils(false);

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
    }

    @Test
    public void shouldSetupScheduledExecutorService() {
        new CaptureProcessScheduler(null, environment, cardCaptureProcess, xrayUtils);

        verify(lifecycleEnvironment).scheduledExecutorService(CAPTURE_PROCESS_SCHEDULER_NAME);
        verify(scheduledExecutorServiceBuilder).threads(SCHEDULER_THREADS);
        verify(scheduledExecutorServiceBuilder).build();
    }

    @Test
    public void shouldScheduleCaptureProcessWithDefaultSchedulingConfiguration() {
        CaptureProcessScheduler captureProcessScheduler = new CaptureProcessScheduler(null, environment, cardCaptureProcess, xrayUtils);
        captureProcessScheduler.start();

        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);

        verify(scheduledExecutorService).scheduleAtFixedRate(any(Runnable.class), eq(INITIAL_DELAY_IN_SECONDS), delay.capture(), eq(TimeUnit.SECONDS));
        assertThat(delay.getValue(), both(greaterThanOrEqualTo(RANDOM_INTERVAL_MINIMUM_IN_SECONDS)).and(lessThan(RANDOM_INTERVAL_MAXIMUM_IN_SECONDS)));
    }

    @Test
    public void shouldScheduleCaptureProcessWithOverriddenSchedulingConfigurationAndRandomTimeInterval() {
        long initialDelayInSeconds = 0L;
        long randomIntervalMinimumInSeconds = 10L;
        long randomIntervalMaximumInSeconds = 20L;

        ConnectorConfiguration mockConnectorConfiguration = mockConnectorConfigurationWith(initialDelayInSeconds, randomIntervalMinimumInSeconds, randomIntervalMaximumInSeconds);

        CaptureProcessScheduler captureProcessScheduler = new CaptureProcessScheduler(mockConnectorConfiguration, environment, cardCaptureProcess, xrayUtils);
        captureProcessScheduler.start();

        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);

        verify(scheduledExecutorService).scheduleAtFixedRate(any(Runnable.class), eq(initialDelayInSeconds), delay.capture(), eq(TimeUnit.SECONDS));
        assertThat(delay.getValue(), both(greaterThanOrEqualTo(randomIntervalMinimumInSeconds)).and(lessThan(randomIntervalMaximumInSeconds)));
    }

    @Test
    public void shouldScheduleCaptureProcessWithOverriddenSchedulingConfigurationAndFixedTimeInterval() {
        long initialDelayInSeconds = 0L;
        long randomIntervalMinimumInSeconds = 10L;
        long randomIntervalMaximumInSeconds = 10L;

        ConnectorConfiguration mockConnectorConfiguration = mockConnectorConfigurationWith(initialDelayInSeconds, randomIntervalMinimumInSeconds, randomIntervalMaximumInSeconds);

        CaptureProcessScheduler captureProcessScheduler = new CaptureProcessScheduler(mockConnectorConfiguration, environment, cardCaptureProcess, xrayUtils);
        captureProcessScheduler.start();

        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);

        verify(scheduledExecutorService).scheduleAtFixedRate(any(Runnable.class), eq(initialDelayInSeconds), delay.capture(), eq(TimeUnit.SECONDS));
        assertThat(delay.getValue(), is(randomIntervalMinimumInSeconds));
    }

    @Test
    public void shouldShutdownScheduledExecutorServiceWhenStopped() {
        CaptureProcessScheduler captureProcessScheduler = new CaptureProcessScheduler(null, environment, cardCaptureProcess, xrayUtils);
        captureProcessScheduler.stop();

        verify(scheduledExecutorService).shutdown();
    }

    private ConnectorConfiguration mockConnectorConfigurationWith(long initialDelayInSeconds, long randomIntervalMinimumInSeconds, long randomIntervalMaximumInSeconds) {
        ConnectorConfiguration mockConnectorConfiguration = mock(ConnectorConfiguration.class);
        CaptureProcessConfig mockCaptureProcessConfig = mock(CaptureProcessConfig.class);

        when(mockCaptureProcessConfig.getSchedulerInitialDelayInSeconds()).thenReturn(initialDelayInSeconds);
        when(mockCaptureProcessConfig.getSchedulerRandomIntervalMinimumInSeconds()).thenReturn(randomIntervalMinimumInSeconds);
        when(mockCaptureProcessConfig.getSchedulerRandomIntervalMaximumInSeconds()).thenReturn(randomIntervalMaximumInSeconds);
        when(mockConnectorConfiguration.getCaptureProcessConfig()).thenReturn(mockCaptureProcessConfig);

        return mockConnectorConfiguration;
    }
}
