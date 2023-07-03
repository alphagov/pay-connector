package uk.gov.pay.connector.paymentprocessor.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.codahale.metrics.Counter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

@ExtendWith(MockitoExtension.class)
class CardCaptureServiceTest extends CardServiceTest {

    @Mock
    private UserNotificationService mockUserNotificationService;
    private CardCaptureService cardCaptureService;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Mock
    private CaptureQueue mockCaptureQueue;
    @Mock
    private ConnectorConfiguration mockConfiguration;
    @Mock
    private Environment mockEnvironment;
    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;
    @Mock
    private StateTransitionService mockStateTransitionService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private EventService mockEventService;    
    @Mock
    private PaymentInstrumentService mockPaymentInstrumentService;
    @Mock 
    private RefundService mockedRefundService;
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;
    @Mock
    protected AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;
    @Mock
    private TaskQueueService mockTaskQueueService;
    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;
    @Mock
    private IdempotencyDao mockIdempotencyDao;
    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    private static final Clock GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK = Clock.fixed(Instant.parse("2020-01-01T10:10:10.100Z"), ZoneOffset.UTC);
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void beforeTest() {
        Counter mockCounter = mock(Counter.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        lenient().when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        ChargeService chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao,
                null, null, null, mockConfiguration, null,
                mockStateTransitionService, ledgerService, mockedRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter,
                mockTaskQueueService, mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory, objectMapper);

        cardCaptureService = new CardCaptureService(chargeService, mockedProviders, mockUserNotificationService, mockEnvironment,
                GREENWICH_MERIDIAN_TIME_OFFSET_CLOCK, mockCaptureQueue, mockEventService);

        Logger root = (Logger) LoggerFactory.getLogger(CardCaptureService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    private void worldpayWillRespondWithSuccess() {
        when(mockedPaymentProvider.capture(any())).thenReturn(
                fromBaseCaptureResponse(BaseCaptureResponse.fromTransactionId(randomUUID().toString(), WORLDPAY), PENDING));
    }

    private void stripeWillRespondWithSuccess() {
        when(mockedPaymentProvider.capture(any())).thenReturn(
                fromBaseCaptureResponse(BaseCaptureResponse.fromTransactionId(randomUUID().toString(), STRIPE), PENDING,
                        List.of(Fee.of(FeeType.TRANSACTION, 50L), Fee.of(FeeType.RADAR, 40L), Fee.of(FeeType.THREE_D_S, 30L))));
    }

    private void worldpayWillRespondWithError() {
        when(mockedPaymentProvider.capture(any())).thenReturn(CaptureResponse.fromGatewayError(genericGatewayError("something went wrong")));
    }

    @Test
    void doCapture_shouldCaptureAChargeForANonSandboxAccount() {

        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);

        worldpayWillRespondWithSuccess();
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        CaptureResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_SUBMITTED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), isNull());

        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getGatewayTransactionId(), is(gatewayTxId));

        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void doCapture_shouldCaptureAChargeForStripeAccount() throws QueueException {

        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWithFees("stripe", 1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);

        stripeWillRespondWithSuccess();
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        CaptureResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_SUBMITTED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), isNull());

        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getGatewayTransactionId(), is(gatewayTxId));

        verify(mockEventService, times(1)).emitAndRecordEvent(any());

        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void doCapture_shouldCaptureAChargeForWorldpayAccountAndShouldNotEmitFeeIncurredEvent() throws QueueException {

        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWithFees("worldpay", 1L, CAPTURE_APPROVED, gatewayTxId);

        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);

        worldpayWillRespondWithSuccess();
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        CaptureResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_SUBMITTED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), isNull());
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getGatewayTransactionId(), is(gatewayTxId));

        verifyNoInteractions(mockEventService);
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void chargeIsCapturedAndEmailNotificationIsSentForDelayedCapture() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("sandbox", 1L, CAPTURE_APPROVED, gatewayTxId);
        charge.setDelayedCapture(true);
        ChargeEntity chargeSpy = spy(charge);

        verifyChargeIsCapturedImmediatelyFromPaymentProvider(chargeSpy);

        verify(mockUserNotificationService).sendPaymentConfirmedEmail(chargeSpy, charge.getGatewayAccount());
    }

    @Test
    void chargeIsCapturedAndNoNotificationIsSentForNonDelayedCapture() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("sandbox", 1L, CAPTURE_APPROVED, gatewayTxId);

        verifyChargeIsCapturedImmediatelyFromPaymentProvider(spy(charge));

        verifyNoInteractions(mockUserNotificationService);
    }

    void verifyChargeIsCapturedImmediatelyFromPaymentProvider(ChargeEntity chargeSpy) {
        mockChargeDaoOperations(chargeSpy);

        when(mockedProviders.byName(chargeSpy.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.capture(any())).thenReturn(
                CaptureResponse.fromBaseCaptureResponse(
                        BaseCaptureResponse.fromTransactionId(randomUUID().toString(), SANDBOX),
                        COMPLETE)
        );
        CaptureResponse response = cardCaptureService.doCapture(chargeSpy.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_SUBMITTED);
        inOrder.verify(chargeSpy).setStatus(CAPTURED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        // charge progresses from CAPTURE_SUBMITTED to CAPTURED, so two calls
        // first invocation will add a captured date
        verify(mockedChargeEventDao, times(2)).persistChargeEventOf(chargeEntityCaptor.capture(), isNull());
        // second invocation will NOT add a captured date
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURED.getValue()));
        // only the CAPTURED has a bookingDate, so there's only one value captured

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getGatewayTransactionId(), is(chargeSpy.getGatewayTransactionId()));
    }


    private void mockChargeDaoOperations(ChargeEntity charge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
    }

    @Test
    void doCapture_shouldGetAChargeNotFound_whenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());
        
        assertThrows(ChargeNotFoundRuntimeException.class, () -> cardCaptureService.doCapture(chargeId));
        // verify an email notification is not sent when an unsuccessful capture
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void doCapture_shouldGetAOperationAlreadyInProgress_whenStatusIsCaptureReady() {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CAPTURE_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        assertThrows(OperationAlreadyInProgressRuntimeException.class, () ->
                cardCaptureService.doCapture(charge.getExternalId()));
        assertThat(charge.getStatus(), is(ChargeStatus.CAPTURE_READY.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void doCapture_shouldGetAnIllegalError_whenChargeHasInvalidStatus() {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.ENTERING_CARD_DETAILS);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        
        assertThrows(IllegalStateRuntimeException.class, () -> cardCaptureService.doCapture(charge.getExternalId()));
        assertThat(charge.getStatus(), is(ChargeStatus.ENTERING_CARD_DETAILS.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void doCapture_shouldGetAConflictError_whenAnOptimisticLockExceptionIsThrown() {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        /*
         * FIXME (PP-2626)
         * This is not going to be thrown from this method, but just to test preOp throwing
         * OptimisticLockException when commit the transaction. We won't do merge in pre-op
         * The related code won't be removed until we know is not an issue doing it so, logging
         * will be in place since there are not evidence (through any test or current logging)
         * that is in reality a subject of a real scenario.
         */
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenThrow(new OptimisticLockException());
        assertThrows(ConflictRuntimeException.class, () -> cardCaptureService.doCapture(charge.getExternalId()));
        assertThat(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void doCapture_shouldSetChargeStatusToCaptureApprovedRetryOnError_whenProviderReturnsErrorResponse() {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("worldpay", 1L, CAPTURE_APPROVED, gatewayTxId);

        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);

        worldpayWillRespondWithError();
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        CaptureResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isSuccessful(), is(false));

        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_APPROVED_RETRY);

        verify(mockedChargeEventDao).persistChargeEventOf(eq(chargeSpy), isNull());

        // verify an email notification is not sent when an unsuccessful capture
        verifyNoInteractions(mockUserNotificationService);
    }

    @Test
    void doCapture_shouldBeAbleToCapture_whenChargeHasAuthorisationSuccessStatus() {
        doCapture_shouldCapture_whenChargeInStatus(AUTHORISATION_SUCCESS);
    }

    @Test
    void doCapture_shouldBeAbleToCapture_whenChargeIsInCaptureApprovedStatus() {
        doCapture_shouldCapture_whenChargeInStatus(CAPTURE_APPROVED);
    }

    @Test
    void doCapture_shouldBeAbleToCapture_whenChargeHasCaptureApprovedRetryStatus() {
        doCapture_shouldCapture_whenChargeInStatus(CAPTURE_APPROVED_RETRY);
    }

    @Test
    void markChargeAsCaptureError_shouldSetChargeStatusToCaptureErrorAndWriteChargeEvent() {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, CAPTURE_APPROVED_RETRY, "gatewayTxId");
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));

        cardCaptureService.markChargeAsCaptureError(charge.getExternalId());

        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_ERROR)), isNull());

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = String.format("CAPTURE_ERROR for charge [charge_external_id=%s] - reached maximum number of capture attempts", charge.getExternalId());
        assertThat(logStatement.get(0).getFormattedMessage(), is(expectedLogMessage));
    }

    @Test
    void markChargeAsCaptureError_shouldIgnore_whenChargeDoesNotExist() {

        String externalId = "external-id";
        when(mockedChargeDao.findByExternalId(externalId)).thenReturn(Optional.empty());

        assertThrows(ChargeNotFoundRuntimeException.class, () -> cardCaptureService.markChargeAsCaptureError(externalId));

        verify(mockedChargeDao).findByExternalId(externalId);
        verifyNoMoreInteractions(mockedChargeDao);
        verify(mockedChargeEventDao, never()).persistChargeEventOf(any(ChargeEntity.class), any(ZonedDateTime.class));
    }

    private void doCapture_shouldCapture_whenChargeInStatus(ChargeStatus chargeStatus) {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, chargeStatus, gatewayTxId);
        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);
        worldpayWillRespondWithSuccess();
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        CaptureResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isSuccessful(), is(true));

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), isNull());
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));
        verify(mockedPaymentProvider, times(1)).capture(any());
    }

    private HamcrestArgumentMatcher<ChargeEntity> chargeEntityHasStatus(ChargeStatus expectedStatus) {
        return new HamcrestArgumentMatcher<>(new TypeSafeMatcher<ChargeEntity>() {
            @Override
            protected boolean matchesSafely(ChargeEntity chargeEntity) {
                return chargeEntity.getStatus().equals(expectedStatus.getValue());
            }

            @Override
            public void describeTo(Description description) {

            }
        });
    }
}
