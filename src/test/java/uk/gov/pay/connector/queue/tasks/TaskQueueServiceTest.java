package uk.gov.pay.connector.queue.tasks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.queue.QueueException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.queue.tasks.TaskQueueService.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME;

@ExtendWith(MockitoExtension.class)
class TaskQueueServiceTest {

    @Mock
    private TaskQueue mockTaskQueue;

    @InjectMocks
    private TaskQueueService taskQueueService;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @BeforeEach
    void setUp() {
        Logger root = (Logger) LoggerFactory.getLogger(TaskQueueService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void shouldOfferFeeTask_whenChargeIsTerminallyFailed_andStripe_andGatewayIdPresent() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        var expectedPaymentTask = new PaymentTask(chargeEntity.getExternalId(), COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME);
        verify(mockTaskQueue).addTaskToQueue(eq(expectedPaymentTask));
    }

    @Test
    void shouldNotOfferFeeTask_whenChargeIsFailedButNotTerminal() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRE_CANCEL_READY)
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldNotOfferFeeTask_whenChargeIsCaptured() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.CAPTURED)
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldOfferFeeTask_whenChargeIsNotStripe() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldOfferFeeTask_whenChargeHasNoGatewayTransationId() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withStatus(ChargeStatus.EXPIRED)
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldSwallowExceptionThrownWhenAddingToQueue() throws Exception {
        doThrow(new QueueException("Something went wrong")).when(mockTaskQueue).addTaskToQueue(any());

        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
        assertThat(loggingEvent.getMessage(), is("Error adding payment task message to queue"));
    }
}
