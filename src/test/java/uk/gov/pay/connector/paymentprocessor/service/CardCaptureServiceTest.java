package uk.gov.pay.connector.paymentprocessor.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.codahale.metrics.Counter;
import io.dropwizard.setup.Environment;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.fee.dao.FeeDao;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.persistence.OptimisticLockException;
import javax.ws.rs.WebApplicationException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureServiceTest extends CardServiceTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private UserNotificationService mockUserNotificationService;
    private CardCaptureService cardCaptureService;
    @Mock
    private FeeDao feeDao;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Mock
    private CaptureQueue mockCaptureQueue;
    @Mock
    private CaptureProcessConfig mockCaptureProcessConfig;
    @Mock
    private ConnectorConfiguration mockConfiguration;
    @Mock
    private Environment mockEnvironment;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Before
    public void beforeTest() {
        Counter mockCounter = mock(Counter.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockCaptureProcessConfig.getCaptureUsingSQS()).thenReturn(true);
        when(mockConfiguration.getCaptureProcessConfig()).thenReturn(mockCaptureProcessConfig);

        chargeService = new ChargeService(null, mockedChargeDao, mockedChargeEventDao,
                null, null, mockConfiguration, null);

        cardCaptureService = new CardCaptureService(chargeService, feeDao, mockedProviders, mockUserNotificationService, mockEnvironment,
                mockCaptureQueue, mockConfiguration);

        Logger root = (Logger) LoggerFactory.getLogger(CardCaptureService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    private void worldpayWillRespondWithSuccess() {
        when(mockedPaymentProvider.capture(any())).thenReturn(
                fromBaseCaptureResponse(BaseCaptureResponse.fromTransactionId(randomUUID().toString(), WORLDPAY), PENDING));
    }

    private void worldpayWillRespondWithError() {
        when(mockedPaymentProvider.capture(any())).thenReturn(CaptureResponse.fromGatewayError(genericGatewayError("something went wrong")));
    }

    @Test
    public void doCapture_shouldCaptureAChargeForANonSandboxAccount() {

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

        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture());

        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).sendPaymentConfirmedEmail(chargeSpy);
    }

    @Test
    public void chargeIsCapturedImmediatelyFromPaymentProvider() {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("sandbox", 1L, CAPTURE_APPROVED, gatewayTxId);
        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.capture(any())).thenReturn(
                CaptureResponse.fromBaseCaptureResponse(
                        BaseCaptureResponse.fromTransactionId(randomUUID().toString(), SANDBOX),
                        COMPLETE)
        );
        CaptureResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_SUBMITTED);
        inOrder.verify(chargeSpy).setStatus(CAPTURED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<ZonedDateTime> bookingDateCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);

        // charge progresses from CAPTURE_SUBMITTED to CAPTURED, so two calls
        // first invocation will add a captured date
        verify(mockedChargeEventDao, times(1)).persistChargeEventOf(chargeEntityCaptor.capture(), bookingDateCaptor.capture());
        // second invocation will NOT add a captured date
        verify(mockedChargeEventDao, times(1)).persistChargeEventOf(chargeEntityCaptor.capture());
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURED.getValue()));

        // only the CAPTURED has a bookingDate, so there's only one value captured
        assertThat(bookingDateCaptor.getAllValues().size(), is(1));
        assertThat(bookingDateCaptor.getAllValues().get(0), is(notNullValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).sendPaymentConfirmedEmail(chargeSpy);
    }

    private void mockChargeDaoOperations(ChargeEntity charge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(any(ChargeEntity.class));
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void doCapture_shouldGetAChargeNotFound_whenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());
        cardCaptureService.doCapture(chargeId);
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldGetAOperationAlreadyInProgress_whenStatusIsCaptureReady() {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CAPTURE_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        exception.expect(OperationAlreadyInProgressRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertThat(charge.getStatus(), is(ChargeStatus.CAPTURE_READY.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldGetAnIllegalError_whenChargeHasInvalidStatus() {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.ENTERING_CARD_DETAILS);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));

        exception.expect(IllegalStateRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertThat(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldGetAConflictError_whenAnOptimisticLockExceptionIsThrown() {
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
        exception.expect(ConflictRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertThat(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldSetChargeStatusToCaptureApprovedRetryOnError_whenProviderReturnsErrorResponse() {
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

        verify(mockedChargeEventDao).persistChargeEventOf(chargeSpy);

        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldBeAbleToCapture_whenChargeHasAuthorisationSuccessStatus() {
        doCapture_shouldCapture_whenChargeInStatus(AUTHORISATION_SUCCESS);
    }

    @Test
    public void doCapture_shouldBeAbleToCapture_whenChargeIsInCaptureApprovedStatus() {
        doCapture_shouldCapture_whenChargeInStatus(CAPTURE_APPROVED);
    }

    @Test
    public void doCapture_shouldBeAbleToCapture_whenChargeHasCaptureApprovedRetryStatus() {
        doCapture_shouldCapture_whenChargeInStatus(CAPTURE_APPROVED_RETRY);
    }

    @Test
    public void markChargeAsCaptureApproved_shouldThrowAnException_whenChargeIsNotFound() {
        String nonExistingChargeExternalId = "non-existing-id";

        when(mockedChargeDao.findByExternalId(nonExistingChargeExternalId)).thenReturn(Optional.empty());

        try {
            cardCaptureService.markChargeAsEligibleForCapture(nonExistingChargeExternalId);
            fail("expecting ChargeNotFoundRuntimeException");
        } catch (ChargeNotFoundRuntimeException e) {
            // ignore
        }

        verify(mockedChargeEventDao, never()).persistChargeEventOf(any(ChargeEntity.class), any(ZonedDateTime.class));
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void markChargeAsCaptureApproved_shouldThrowAnIllegalStateRuntimeException_whenChargeIsNotInAuthorisationSuccess() {
        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, CAPTURE_READY, "gatewayTxId"));

        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        try {
            cardCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());
        } catch (IllegalStateRuntimeException e) {
            verify(mockedChargeDao).findByExternalId(chargeEntity.getExternalId());
            verifyNoMoreInteractions(mockedChargeDao);
            throw e;
        }

    }

    @Test
    public void markChargeAsCaptureApproved_shouldSetChargeStatusToCaptureApprovedAndWriteChargeEvent() {
        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, "gatewayTxId"));
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(chargeEntity);

        ChargeEntity result = cardCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());

        verify(chargeEntity).setStatus(CAPTURE_APPROVED);
        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_APPROVED)));
        assertThat(result.getStatus(), is(CAPTURE_APPROVED.getValue()));
    }

    @Test
    public void markChargeAsCaptureApproved_shouldSetChargeStatusToAwaitingCaptureRequestWhenDelayedCapture() {
        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, "gatewayTxId"));
        chargeEntity.setDelayedCapture(true);
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(chargeEntity);

        ChargeEntity result = cardCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());

        verify(chargeEntity).setStatus(AWAITING_CAPTURE_REQUEST);
        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(AWAITING_CAPTURE_REQUEST)));
        assertThat(result.getStatus(), is(AWAITING_CAPTURE_REQUEST.getValue()));
    }

    @Test
    public void markChargeAsCaptureError_shouldSetChargeStatusToCaptureErrorAndWriteChargeEvent() {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, CAPTURE_APPROVED_RETRY, "gatewayTxId");
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(charge);

        cardCaptureService.markChargeAsCaptureError(charge.getExternalId());

        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_ERROR)));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = String.format("CAPTURE_ERROR for charge [charge_external_id=%s] - reached maximum number of capture attempts", charge.getExternalId());
        assertThat(logStatement.get(0).getFormattedMessage(), is(expectedLogMessage));
    }

    @Test
    public void markChargeAsCaptureError_shouldIgnore_whenChargeDoesNotExist() {

        String externalId = "external-id";
        when(mockedChargeDao.findByExternalId(externalId)).thenReturn(Optional.empty());

        exception.expect(ChargeNotFoundRuntimeException.class);
        cardCaptureService.markChargeAsCaptureError(externalId);

        verify(mockedChargeDao).findByExternalId(externalId);
        verifyNoMoreInteractions(mockedChargeDao);
        verify(mockedChargeEventDao, never()).persistChargeEventOf(any(ChargeEntity.class), any(ZonedDateTime.class));
    }

    @Test
    public void markChargeAsCaptureApproved_shouldSuccess_whenChargeStatusIs_awaitingCaptureRequest() {
        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, AWAITING_CAPTURE_REQUEST, "gatewayTxId"));
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(chargeEntity);

        ChargeEntity result = cardCaptureService.markChargeAsCaptureApproved(chargeEntity.getExternalId());

        verify(chargeEntity).setStatus(CAPTURE_APPROVED);
        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_APPROVED)));
        assertThat(result.getStatus(), is(CAPTURE_APPROVED.getValue()));
    }

    @Test
    public void markChargeAsCaptureApproved_shouldSuccess_whenChargeInACaptureState() {
        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, CAPTURE_APPROVED, "gatewayTxId"));
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = cardCaptureService.markChargeAsCaptureApproved(chargeEntity.getExternalId());

        verifyNoMoreInteractions(mockedChargeEventDao);
        assertThat(result.getStatus(), is(CAPTURE_APPROVED.getValue()));
    }

    @Test
    public void markChargeAsCaptureApproved_shouldThrowChargeNotFoundException_whenChargeDoesNotExist() {
        String externalId = "external-id";
        when(mockedChargeDao.findByExternalId(externalId)).thenReturn(Optional.empty());

        exception.expect(ChargeNotFoundRuntimeException.class);
        cardCaptureService.markChargeAsCaptureApproved(externalId);

        verify(mockedChargeDao).findByExternalId(externalId);
        verifyNoMoreInteractions(mockedChargeDao);
        verify(mockedChargeEventDao, never()).persistChargeEventOf(any(ChargeEntity.class), any(ZonedDateTime.class));
    }

    @Test
    public void markChargeAsCaptureApproved_shouldThrow_conflictRuntimeException_whenChargeStateCannotTransition() {
        String externalId = "external-id";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, EXPIRED, "gatewayTxId");
        when(mockedChargeDao.findByExternalId(externalId)).thenReturn(Optional.of(charge));

        exception.expect(ConflictRuntimeException.class);
        cardCaptureService.markChargeAsCaptureApproved(externalId);

        verify(mockedChargeDao).findByExternalId(externalId);
        verifyNoMoreInteractions(mockedChargeDao);
        verify(mockedChargeEventDao, never()).persistChargeEventOf(any(ChargeEntity.class), any(ZonedDateTime.class));
    }

    @Test
    public void markChargeAsEligibleForCapture_shouldAddChargeToQueueAndUpdateChargeStatus() throws QueueException {

        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, "gatewayTxId"));
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = cardCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());

        verify(mockCaptureQueue).sendForCapture(chargeEntity.getExternalId());

        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_APPROVED)));
        assertThat(result.getStatus(), is(CAPTURE_APPROVED.getValue()));
    }

    @Test
    public void markChargeAsEligibleForCapture_shouldNotAddChargeToQueue_IfChargeIsDelayedCapture() {

        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, "gatewayTxId"));
        chargeEntity.setDelayedCapture(true);
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = cardCaptureService.markChargeAsEligibleForCapture(chargeEntity.getExternalId());

        verifyZeroInteractions(mockCaptureQueue);

        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(AWAITING_CAPTURE_REQUEST)));
        assertThat(result.getStatus(), is(AWAITING_CAPTURE_REQUEST.getValue()));
    }

    @Test(expected = WebApplicationException.class)
    public void markChargeAsEligibleForCapture_shouldThrowException_WithFeatureFlagEnabledAndUnableToAddChargeToQueue() throws QueueException {

        when(mockCaptureProcessConfig.getCaptureUsingSQS()).thenReturn(true);
        doThrow(new QueueException()).when(mockCaptureQueue).sendForCapture(anyString());

        CardCaptureService cardCaptureService = new CardCaptureService(chargeService, feeDao, mockedProviders, mockUserNotificationService,
                mockEnvironment, mockCaptureQueue,
                mockConfiguration);

        String externalId = "external-id";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, EXPIRED, "gatewayTxId");
        when(mockedChargeDao.findByExternalId(externalId)).thenReturn(Optional.of(charge));

        try {
            cardCaptureService.markChargeAsEligibleForCapture(externalId);
        } catch (WebApplicationException e) {
            verify(mockedChargeDao).findByExternalId(externalId);
            verify(mockedChargeEventDao, never()).persistChargeEventOf(any(ChargeEntity.class), any(ZonedDateTime.class));
            verifyNoMoreInteractions(mockedChargeDao);
            throw e;
        }
    }

    @Test
    public void markChargeAsCaptureApproved_shouldAddChargeToQueueAndUpdateChargeStatus() throws QueueException {

        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, "gatewayTxId"));
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        ChargeEntity result = cardCaptureService.markChargeAsCaptureApproved(chargeEntity.getExternalId());

        verify(mockCaptureQueue).sendForCapture(chargeEntity.getExternalId());

        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_APPROVED)));
        assertThat(result.getStatus(), is(CAPTURE_APPROVED.getValue()));
    }

    @Test(expected = WebApplicationException.class)
    public void markChargeAsCaptureApproved_shouldThrowException_WithFeatureFlagEnabledAndUnableToAddChargeToQueue() throws QueueException {
        when(mockCaptureProcessConfig.getCaptureUsingSQS()).thenReturn(true);
        doThrow(new QueueException()).when(mockCaptureQueue).sendForCapture(anyString());

        CardCaptureService cardCaptureService = new CardCaptureService(chargeService, feeDao, mockedProviders, mockUserNotificationService,
                mockEnvironment, mockCaptureQueue,
                mockConfiguration);

        try {
            cardCaptureService.markChargeAsCaptureApproved("external-id");
        } catch (WebApplicationException e) {
            verifyNoMoreInteractions(mockedChargeDao);
            throw e;
        }
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
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture());
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
