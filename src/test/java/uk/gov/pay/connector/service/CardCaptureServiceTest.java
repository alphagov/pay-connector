package uk.gov.pay.connector.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;

import javax.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureServiceTest extends CardServiceTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private UserNotificationService mockUserNotificationService;
    private CardCaptureService cardCaptureService;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Before
    public void beforeTest() {
        Environment mockEnvironment = mock(Environment.class);
        mockMetricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        cardCaptureService = new CardCaptureService(mockedChargeDao, mockedChargeEventDao, mockedProviders, mockUserNotificationService, mockEnvironment);

        Logger root = (Logger) LoggerFactory.getLogger(CardCaptureService.class);
        root.addAppender(mockAppender);
    }

    private void worldpayWillRespondWithSuccess(String transactionId, String worldpayErrorCode) {
        WorldpayCaptureResponse worldpayResponse = mock(WorldpayCaptureResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.getErrorCode()).thenReturn(worldpayErrorCode);
        GatewayResponseBuilder<WorldpayCaptureResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse captureResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();
        when(mockedPaymentProvider.capture(any())).thenReturn(captureResponse);
    }

    private void worldpayWillRespondWithError() {
        GatewayResponseBuilder<WorldpayCaptureResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse captureResponse = gatewayResponseBuilder
                .withGatewayError(GatewayError.baseError("something went wrong")).build();
        when(mockedPaymentProvider.capture(any())).thenReturn(captureResponse);
    }

    class EmptyOptionalMatcher extends BaseMatcher<Optional<?>> {
        @Override
        public boolean matches(Object item) {
            return !((Optional)item).isPresent();
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("An empty optional");
        }
    }

    @Test
    public void doCapture_shouldCaptureAChargeForANonSandboxAccount() throws Exception {

        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay",1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);

        worldpayWillRespondWithSuccess(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_SUBMITTED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), eq(Optional.empty()));

        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).notifyPaymentSuccessEmail(chargeSpy);
    }

    @Test
    public void doCapture_shouldCaptureAChargeForASandboxAccount() throws Exception {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("sandbox",1L, CAPTURE_APPROVED, gatewayTxId);
        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);

        worldpayWillRespondWithSuccess(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_SUBMITTED);
        inOrder.verify(chargeSpy).setStatus(CAPTURED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<Optional> bookingDateCaptor = ArgumentCaptor.forClass(Optional.class);

        // sandbox progresses from CAPTURE_SUBMITTED to CAPTURED, so two calls
        verify(mockedChargeEventDao, times(2)).persistChargeEventOf(chargeEntityCaptor.capture(), bookingDateCaptor.capture());
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURED.getValue()));

        // only the CAPTURED has a bookingDate
        assertFalse(bookingDateCaptor.getAllValues().get(0).isPresent());
        assertTrue(bookingDateCaptor.getAllValues().get(1).isPresent());

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).notifyPaymentSuccessEmail(chargeSpy);
    }

    private void mockChargeDaoOperations(ChargeEntity charge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(any(), any());
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
    public void doCapture_shouldGetAOperationAlreadyInProgress_whenStatusIsCaptureReady() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CAPTURE_READY);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);
        exception.expect(OperationAlreadyInProgressRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertEquals(charge.getStatus(), is(ChargeStatus.CAPTURE_READY.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldGetAnIllegalError_whenChargeHasInvalidStatus() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.ENTERING_CARD_DETAILS);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        exception.expect(IllegalStateRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertEquals(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldGetAConflictError_whenAnOptimisticLockExceptionIsThrown() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        /**
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
        assertEquals(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
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

        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isFailed(), is(true));

        InOrder inOrder = Mockito.inOrder(chargeSpy);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_READY);
        inOrder.verify(chargeSpy).setStatus(CAPTURE_APPROVED_RETRY);

        verify(mockedChargeEventDao).persistChargeEventOf(chargeSpy, Optional.empty());

        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void doCapture_shouldBeAbleToCapture_whenChargeHasAuthorisationSuccessStatus() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, gatewayTxId);
        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);
        worldpayWillRespondWithSuccess(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isSuccessful(), is(true));

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), eq(Optional.empty()));
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));
        verify(mockedPaymentProvider, times(1)).capture(any());
    }

    @Test
    public void doCapture_shouldBeAbleToCapture_whenChargeIsInCaptureApprovedStatus() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, CAPTURE_APPROVED, gatewayTxId);
        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);
        worldpayWillRespondWithSuccess(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isSuccessful(), is(true));

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), eq(Optional.empty()));
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));
        verify(mockedPaymentProvider, times(1)).capture(any());
    }

    @Test
    public void doCapture_shouldBeAbleToCapture_whenChargeHasCaptureApprovedRetryStatus() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, CAPTURE_APPROVED_RETRY, gatewayTxId);
        ChargeEntity chargeSpy = spy(charge);
        mockChargeDaoOperations(chargeSpy);
        worldpayWillRespondWithSuccess(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isSuccessful(), is(true));

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeEventDao).persistChargeEventOf(chargeEntityCaptor.capture(), eq(Optional.empty()));
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));
        verify(mockedPaymentProvider, times(1)).capture(any());
    }

    @Test
    public void markChargeAsCaptureApproved_shouldThrowAnException_whenChargeIsNotFound() {
        String nonExistingChargeExternalId = "non-existing-id";

        when(mockedChargeDao.findByExternalId(nonExistingChargeExternalId)).thenReturn(Optional.empty());

        try {
            cardCaptureService.markChargeAsCaptureApproved(nonExistingChargeExternalId);
            fail("expecting ChargeNotFoundRuntimeException");
        } catch (ChargeNotFoundRuntimeException e) {
            // ignore
        }

        verify(mockedChargeEventDao, never()).persistChargeEventOf(any(), any());
    }

    @Test
    public void markChargeAsCaptureApproved_shouldThrowAnIllegalStateRuntimeException_whenChargeIsNotInAuthorisationSuccess() {
        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, CAPTURE_READY, "gatewayTxId"));

        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));

        try {
            cardCaptureService.markChargeAsCaptureApproved(chargeEntity.getExternalId());
            fail("expecting IllegalStateRuntimeException");
        } catch (IllegalStateRuntimeException e) {
            // ignore
        }

        verify(mockedChargeDao).findByExternalId(chargeEntity.getExternalId());
        verify(chargeEntity, never()).setStatus(any());
        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        verifyNoMoreInteractions(mockedChargeDao);
    }

    @Test
    public void markChargeAsCaptureApproved_shouldSetChargeStatusToCaptureApprovedAndWriteChargeEvent() {
        ChargeEntity chargeEntity = spy(createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, "gatewayTxId"));
        when(mockedChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(chargeEntity, Optional.empty());

        ChargeEntity result = cardCaptureService.markChargeAsCaptureApproved(chargeEntity.getExternalId());

        verify(chargeEntity).setStatus(CAPTURE_APPROVED);
        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_APPROVED)), eq(Optional.empty()));
        assertThat(result.getStatus(), is(CAPTURE_APPROVED.getValue()));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
    }

    @Test
    public void markChargeAsCaptureError_shouldSetChargeStatusToCaptureErrorAndWriteChargeEvent() {
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, CAPTURE_APPROVED_RETRY, "gatewayTxId");
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        doNothing().when(mockedChargeEventDao).persistChargeEventOf(charge, Optional.empty());

        cardCaptureService.markChargeAsCaptureError(charge.getExternalId());

        verify(mockedChargeEventDao).persistChargeEventOf(argThat(chargeEntityHasStatus(CAPTURE_ERROR)), eq(Optional.empty()));

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = String.format("CAPTURE_ERROR for charge [charge_external_id=%s] - reached maximum number of capture attempts", charge.getExternalId());
        Assert.assertThat(logStatement.get(0).getFormattedMessage(), is(expectedLogMessage));
    }

    @Test
    public void markChargeAsCaptureError_shouldIgnore_whenChargeDoesNotExist() {

        String externalId = "external-id";
        when(mockedChargeDao.findByExternalId(externalId)).thenReturn(Optional.empty());

        cardCaptureService.markChargeAsCaptureError(externalId);

        verify(mockedChargeDao).findByExternalId(externalId);
        verifyNoMoreInteractions(mockedChargeDao);
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
