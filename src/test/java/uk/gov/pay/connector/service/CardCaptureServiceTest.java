package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.persistence.OptimisticLockException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
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
    public void beforeTest(){
        cardCaptureService = new CardCaptureService(mockedChargeDao, mockedProviders, mockUserNotificationService);
    }

    @Test
    public void shouldCaptureACharge() throws Exception {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        mockSuccessfulCapture();
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());

        assertThat(response, is(aSuccessfulResponse()));
        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureGatewayRequest> request = ArgumentCaptor.forClass(CaptureGatewayRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));

        // verify an email notification is sent for a successful capture
        verify(mockUserNotificationService).notifyPaymentSuccessEmail(charge);
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
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);
        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge)
                .thenReturn(reloadedCharge);
        mockUnsuccessfulCapture();
        GatewayResponse response = cardCaptureService.doCapture(charge.getExternalId());
        assertThat(response, is(anUnSuccessfulResponse()));
        verify(reloadedCharge, times(1)).setStatus(CAPTURE_ERROR);

        // verify an email notification is not sent when an unsuccessful capture
        verifyZeroInteractions(mockUserNotificationService);
    }

    private void mockSuccessfulCapture() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.capture(any()))
                .thenReturn(CaptureGatewayResponse.successfulCaptureResponse(CAPTURE_SUBMITTED));
    }

    private void mockUnsuccessfulCapture() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.capture(any()))
                .thenReturn(CaptureGatewayResponse.captureFailureResponse(ErrorResponse.baseError("error")));
    }
}
