package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureMessageProcess;
import uk.gov.pay.connector.queue.CaptureQueue;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class QueueMessageReceiverTest {
    
    @Mock
    private CardCaptureMessageProcess cardCaptureMessageProcess;

    @Mock
    private Environment environment;

    @Mock
    private LifecycleEnvironment lifecycleEnvironment;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    private ScheduledExecutorServiceBuilder scheduledExecutorServiceBuilder;

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
    }

    
    @Test
    public void shouldSetupScheduledExecutorService() {
        String EXPECTED_SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-receiver";
        int EXPECTED_TOTAL_MESSAGE_RECEIVER_THREADS = 1;
        
        new QueueMessageReceiver(cardCaptureMessageProcess, environment);

        verify(lifecycleEnvironment).scheduledExecutorService(EXPECTED_SQS_MESSAGE_RECEIVER_THREAD_NAME);
        verify(scheduledExecutorServiceBuilder).threads(EXPECTED_TOTAL_MESSAGE_RECEIVER_THREADS);
        verify(scheduledExecutorServiceBuilder).build();
    }

    @Test
    public void shouldShutdownScheduledExecutorServiceWhenStopped() {
        QueueMessageReceiver queueMessageReceiver = new QueueMessageReceiver(cardCaptureMessageProcess, environment);
        queueMessageReceiver.stop();

        verify(scheduledExecutorService).shutdown();
    }
}
