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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.queue.tasks.TaskQueueService.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME;

@ExtendWith(MockitoExtension.class)
class TaskQueueMessageHandlerTest {
    
    @Mock
    private TaskQueue taskQueue;
    
    @Mock
    private ChargeService chargeService;
    
    @Mock
    private GatewayAccountDao gatewayAccountDao;
    
    @Mock
    private GatewayAccountCredentialsService gatewayAccountCredentialsService;
    
    @Mock
    private StripePaymentProvider stripePaymentProvider;
    
    @Mock
    private Appender<ILoggingEvent> logAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;
    
    private TaskQueueMessageHandler taskQueueMessageHandler;
    
    
    private final String chargeExternalId = "a-charge-external-id";
    
    private final GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
            .withPaymentProvider("stripe")
            .build();
    private final GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
            .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
            .build();
    private final ChargeEntity chargeEntity = aValidChargeEntity()
            .withExternalId(chargeExternalId)
            .withGatewayAccountEntity(gatewayAccountEntity)
            .withStatus(ChargeStatus.EXPIRED)
            .build();
    private final Charge charge = Charge.from(chargeEntity);

    @BeforeEach
    public void setup() {
        taskQueueMessageHandler = new TaskQueueMessageHandler(
                taskQueue,
                chargeService,
                gatewayAccountDao,
                gatewayAccountCredentialsService,
                stripePaymentProvider);
        
        Logger errorLogger = (Logger) LoggerFactory.getLogger(TaskQueueMessageHandler.class);
        errorLogger.setLevel(Level.ERROR);
        errorLogger.addAppender(logAppender);
    }

    @Test
    public void shouldProcessCollectFeeTask() throws Exception {
        PaymentTaskMessage paymentTaskMessage = setupQueueMessage(chargeExternalId, COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME);
        when(chargeService.findCharge(chargeExternalId)).thenReturn(Optional.of(charge));
        when(gatewayAccountDao.findById(gatewayAccountEntity.getId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        taskQueueMessageHandler.processMessages();

        verify(stripePaymentProvider).transferFeesForFailedPayments(charge, gatewayAccountEntity, gatewayAccountCredentialsEntity);
        verify(taskQueue).markMessageAsProcessed(paymentTaskMessage.getQueueMessage());
    }

    @Test
    public void shouldLogErrorIfTaskNameIsNotRecognised() throws QueueException {
        PaymentTaskMessage paymentTaskMessage = setupQueueMessage("payment-external-id-123", "unknown-task-name");

        taskQueueMessageHandler.processMessages();

        verify(taskQueue).markMessageAsProcessed(paymentTaskMessage.getQueueMessage());

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Task [unknown-task-name] is not supported"));
    }

    private PaymentTaskMessage setupQueueMessage(String paymentExternalId, String task) throws QueueException {
        PaymentTask paymentTask = new PaymentTask(paymentExternalId, task);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        PaymentTaskMessage paymentTaskMessage = PaymentTaskMessage.of(paymentTask, mockQueueMessage);
        when(taskQueue.retrieveTaskQueueMessages()).thenReturn(List.of(paymentTaskMessage));
        return paymentTaskMessage;
    }
}
