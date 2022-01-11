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
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.app.config.TaskQueueConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.queue.QueueException;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.queue.tasks.TaskQueueService.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME;

@ExtendWith(MockitoExtension.class)
class TaskQueueServiceTest {

    @Mock
    private TaskQueue mockTaskQueue;
    
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    
    @Mock
    private TaskQueueConfig mockTaskQueueConfig;

    @Mock
    private StripeGatewayConfig mockStripeGatewayConfig;

    @InjectMocks
    private TaskQueueService taskQueueService;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private int chargeCreatedBeforeDate = 1629849600; //25Aug2021
    private int feeCollectingStartsDate = 1629936000; //26 Aug2021
    private int chargeCreatedAfterDate =  1630105200; //27 Aug2021

    @BeforeEach
    void setUp() {
        Logger root = (Logger) LoggerFactory.getLogger(TaskQueueService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        when(mockStripeGatewayConfig.getCollectFeeForStripeFailedPaymentsFromDate())
                .thenReturn(Instant.ofEpochSecond(feeCollectingStartsDate));
    }

    @Test
    void shouldOfferFeeTask_whenChargeIsTerminallyFailed_andStripe_andGatewayIdPresent() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedBeforeDate))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
                .build();

        when(mockStripeGatewayConfig.getCollectFeeForStripeFailedPaymentsFromDate())
                .thenReturn(Instant.ofEpochSecond(feeCollectingStartsDate));
        when(mockStripeGatewayConfig.isEnableTransactionFeeV2ForTestAccounts()).thenReturn(false);
        when(mockStripeGatewayConfig.getEnableTransactionFeeV2ForGatewayAccountsList())
                .thenReturn(List.of(String.valueOf(chargeEntity.getGatewayAccount().getId())));

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
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedBeforeDate))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
                .build();

        setupStripeGatewayConfigMock("12");

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldNotOfferFeeTask_whenChargeIsCaptured() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.CAPTURED)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedAfterDate))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldNotOfferFeeTask_whenChargeIsNotStripe() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("worldpay")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldNotOfferFeeTask_whenChargeHasNoGatewayTransationId() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withStatus(ChargeStatus.EXPIRED)
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
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
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(TEST)
                        .build())
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
        assertThat(loggingEvent.getMessage(), is("Error adding payment task message to queue"));
    }

    @Test
    void shouldNotOfferFeeTask_whenCollectFeeDateIsInTheFuture() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedBeforeDate))
                .build();

        when(mockStripeGatewayConfig.getCollectFeeForStripeFailedPaymentsFromDate())
                .thenReturn(Instant.ofEpochSecond(feeCollectingStartsDate));

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    @Test
    void shouldOfferFeeTask_whenTestGatewayAccountIsEnabled() throws Exception {
        var failedFeeDateAfterChargeCreatedDate = 1629936000; //26 Aug2021
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedBeforeDate))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(TEST)
                        .withId(12L)
                        .build())
                .build();

        when(mockStripeGatewayConfig.getCollectFeeForStripeFailedPaymentsFromDate())
                .thenReturn(Instant.ofEpochSecond(feeCollectingStartsDate));
        when(mockStripeGatewayConfig.isEnableTransactionFeeV2ForTestAccounts()).thenReturn(true);

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        var expectedPaymentTask = new PaymentTask(chargeEntity.getExternalId(), COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME);
        verify(mockTaskQueue).addTaskToQueue(eq(expectedPaymentTask));
    }

    @Test
    void shouldOfferFeeTask_whenTestGatewayAccountIsInEnabledList() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedBeforeDate))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
                .build();

        setupStripeGatewayConfigMock("12");
        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        var expectedPaymentTask = new PaymentTask(chargeEntity.getExternalId(), COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME);
        verify(mockTaskQueue).addTaskToQueue(eq(expectedPaymentTask));
    }

    @Test
    void shouldOfferFeeTask_whenCreatedDateIsAfterCollectEnableDate() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedAfterDate))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        var expectedPaymentTask = new PaymentTask(chargeEntity.getExternalId(), COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME);
        verify(mockTaskQueue).addTaskToQueue(eq(expectedPaymentTask));
    }

    @Test
    void shouldOfferFeeTask_whenCreatedDateIsOnCollectEnableDate() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withCreatedDate(Instant.ofEpochSecond(1629936000))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(LIVE)
                        .build())
                .build();

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        var expectedPaymentTask = new PaymentTask(chargeEntity.getExternalId(), COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME);
        verify(mockTaskQueue).addTaskToQueue(eq(expectedPaymentTask));
    }

    @Test
    void shouldNotOfferFeeTask_whenNoneOfTheConditionsMet() throws Exception {
        var chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId("a-gateway-transaction-id")
                .withStatus(ChargeStatus.EXPIRED)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedBeforeDate))
                .withGatewayAccountEntity(aGatewayAccountEntity()
                        .withId(12L)
                        .withGatewayName("stripe")
                        .withRequires3ds(false)
                        .withType(TEST)
                        .build())
                .build();

        setupStripeGatewayConfigMock("1");

        taskQueueService.offerTasksOnStateTransition(chargeEntity);

        verify(mockTaskQueue, never()).addTaskToQueue(any());
    }

    private void setupStripeGatewayConfigMock(String gatewayAccountId) {
        when(mockStripeGatewayConfig.isEnableTransactionFeeV2ForTestAccounts()).thenReturn(false);
        when(mockStripeGatewayConfig.getEnableTransactionFeeV2ForGatewayAccountsList())
                .thenReturn(List.of(gatewayAccountId));
    }
}
