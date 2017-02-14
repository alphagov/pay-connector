package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;

import javax.persistence.OptimisticLockException;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureServiceTest extends CardServiceTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private UserNotificationService mockUserNotificationService;
    private CardCaptureService cardCaptureService;

    @Before
    public void beforeTest() {
        Environment mockEnvironment = mock(Environment.class);
        mockMetricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        cardCaptureService = new CardCaptureService(mockedChargeDao, mockedProviders, mockUserNotificationService, mockEnvironment);
    }

    public void setupPaymentProviderMock(String transactionId, String errorCode) {
        WorldpayCaptureResponse worldpayResponse = mock(WorldpayCaptureResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponse captureResponse = GatewayResponse.with(worldpayResponse);
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
    public void shouldCaptureAChargeForANonSandboxAccount() throws Exception {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("worldpay",1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity reloadedCharge = spy(charge);
        mockChargeDaoOperations(charge, reloadedCharge);

        setupPaymentProviderMock(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(reloadedCharge);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_READY);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_SUBMITTED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(chargeEntityCaptor.capture(), eq(Optional.empty()));

        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).notifyPaymentSuccessEmail(reloadedCharge);
    }

    @Test
    public void shouldCaptureAChargeForASandboxAccount() throws Exception {
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith("sandbox",1L, AUTHORISATION_SUCCESS, gatewayTxId);

        ChargeEntity reloadedCharge = spy(charge);

        mockChargeDaoOperations(charge, reloadedCharge);
        when(mockedChargeDao.mergeAndNotifyStatusHasChanged(any(), any())).thenReturn(reloadedCharge);

        setupPaymentProviderMock(gatewayTxId, null);
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response.isSuccessful(), is(true));
        InOrder inOrder = Mockito.inOrder(reloadedCharge);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_READY);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_SUBMITTED);
        inOrder.verify(reloadedCharge).setStatus(CAPTURED);

        ArgumentCaptor<ChargeEntity> chargeEntityCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<Optional> bookingDateCaptor = ArgumentCaptor.forClass(Optional.class);

        // sandbox progresses from CAPTURE_SUBMITTED to CAPTURED, so two calls
        verify(mockedChargeDao, times(2)).mergeAndNotifyStatusHasChanged(chargeEntityCaptor.capture(), bookingDateCaptor.capture());
        assertThat(chargeEntityCaptor.getValue().getStatus(), is(CAPTURED.getValue()));

        // only the CAPTURED has a bookingDate
        assertFalse(bookingDateCaptor.getAllValues().get(0).isPresent());
        assertTrue(bookingDateCaptor.getAllValues().get(1).isPresent());

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).notifyPaymentSuccessEmail(reloadedCharge);
    }

    private void mockChargeDaoOperations(ChargeEntity charge, ChargeEntity reloadedCharge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(reloadedCharge);
        when(mockedChargeDao.mergeAndNotifyStatusHasChanged(any(), any()))
                .thenReturn(reloadedCharge);
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldGetAChargeNotFoundWhenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());
        cardCaptureService.doCapture(chargeId);
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void shouldGetAOperationAlreadyInProgressWhenStatusIsCaptureReady() throws Exception {
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
    public void shouldGetAIllegalErrorWhenInvalidStatus() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);
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
    public void shouldGetAConflictErrorWhenConflicting() throws Exception {
        Long chargeId = 1234L;
        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenThrow(new OptimisticLockException());
        exception.expect(ConflictRuntimeException.class);
        cardCaptureService.doCapture(charge.getExternalId());
        assertEquals(charge.getStatus(), is(ChargeStatus.CREATED.getValue()));
        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    @Test
    public void shouldUpdateChargeWithCaptureErrorWhenCaptureFails() {
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith("worldpay", 1L, AUTHORISATION_SUCCESS, gatewayTxId);
        ChargeEntity reloadedCharge = spy(charge);

        mockChargeDaoOperations(charge, reloadedCharge);

        setupPaymentProviderMock(gatewayTxId, "error-code");
        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);

        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response.isFailed(), is(true));

        InOrder inOrder = Mockito.inOrder(reloadedCharge);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_READY);
        inOrder.verify(reloadedCharge).setStatus(CAPTURE_ERROR);

        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

}
