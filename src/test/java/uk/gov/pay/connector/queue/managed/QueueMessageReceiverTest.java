package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.PaymentStateTransitionEmitterProcess;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class QueueMessageReceiverTest {

    @Mock
    private CardCaptureProcess cardCaptureProcess;

    @Mock
    private PaymentStateTransitionEmitterProcess paymentStateTransitionEmitterProcess;

    @Mock
    private Environment environment;

    @Mock
    private LifecycleEnvironment lifecycleEnvironment;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Mock
    private ConnectorConfiguration connectorConfiguration;

    private ScheduledExecutorServiceBuilder scheduledExecutorServiceBuilder;

    private final static int EXPECTED_TOTAL_MESSAGE_RECEIVER_THREADS = 1;

    @Before
    public void setUp() {
        scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class, invocation -> {
            Object mock = invocation.getMock();
            if (invocation.getMethod().getReturnType().isInstance(mock)) {
                return mock;
            } else {
                return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        when(scheduledExecutorServiceBuilder.build()).thenReturn(scheduledExecutorService);
        when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
        when(lifecycleEnvironment.scheduledExecutorService(anyString())).thenReturn(scheduledExecutorServiceBuilder);

        CaptureProcessConfig captureProcessConfig = mock(CaptureProcessConfig.class);
        when(captureProcessConfig.getQueueSchedulerNumberOfThreads()).thenReturn(EXPECTED_TOTAL_MESSAGE_RECEIVER_THREADS);
        when(captureProcessConfig.getQueueSchedulerThreadDelayInSeconds()).thenReturn(1);

        when(connectorConfiguration.getCaptureProcessConfig()).thenReturn(captureProcessConfig);
    }


    @Test
    public void shouldSetupScheduledExecutorService() {
        String EXPECTED_SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-receiver";

        new QueueMessageReceiver(cardCaptureProcess, paymentStateTransitionEmitterProcess, environment, connectorConfiguration);

        verify(lifecycleEnvironment).scheduledExecutorService(EXPECTED_SQS_MESSAGE_RECEIVER_THREAD_NAME);
        verify(scheduledExecutorServiceBuilder).threads(EXPECTED_TOTAL_MESSAGE_RECEIVER_THREADS);
        verify(scheduledExecutorServiceBuilder).build();
    }

    @Test
    public void shouldShutdownScheduledExecutorServiceWhenStopped() {
        QueueMessageReceiver queueMessageReceiver = new QueueMessageReceiver(cardCaptureProcess, paymentStateTransitionEmitterProcess, environment, connectorConfiguration);
        queueMessageReceiver.stop();

        verify(scheduledExecutorService).shutdown();
    }
}
