package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardCaptureService;

import javax.persistence.OptimisticLockException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.GatewayErrorType.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureServiceTest extends CardServiceTest {
    private final CardCaptureService cardCaptureService = new CardCaptureService(mockedAccountDao, mockedChargeDao, mockedProviders);

    @Test
    public void shouldCaptureACharge() throws Exception {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);

        when(mockedChargeDao.findById(charge.getId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        mockSuccessfulCapture();

        Either<GatewayError, GatewayResponse> response = cardCaptureService.doCapture(chargeId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureRequest> request = ArgumentCaptor.forClass(CaptureRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));
    }

    @Test
    public void shouldGetAChargeNotFoundWhenChargeDoesNotExist() {

        Long chargeId = 45678L;

        when(mockedChargeDao.findById(chargeId))
                .thenReturn(Optional.empty());

        Either<GatewayError, GatewayResponse> response = cardCaptureService.doCapture(chargeId);

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(gatewayError.getMessage(), is("Charge with id [45678] not found."));
    }

    @Test
    public void shouldGetAOperationAlreadyInProgressWhenStatusIsCaptureReady() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CAPTURE_READY);

        when(mockedChargeDao.findById(charge.getId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        Either<GatewayError, GatewayResponse> response = cardCaptureService.doCapture(chargeId);

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(OPERATION_ALREADY_IN_PROGRESS));
        assertThat(gatewayError.getMessage(), is("Capture for charge already in progress, 1234"));
    }

    @Test
    public void shouldGetAIllegalErrorWhenInvalidStatus() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        when(mockedChargeDao.findById(charge.getId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        Either<GatewayError, GatewayResponse> response = cardCaptureService.doCapture(chargeId);

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(ILLEGAL_STATE_ERROR));
        assertThat(gatewayError.getMessage(), is("Charge not in correct state to be processed, 1234"));
    }

    @Test
    public void shouldGetAConflictErrorWhenConflicting() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        when(mockedChargeDao.findById(charge.getId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenThrow(new OptimisticLockException());

        Either<GatewayError, GatewayResponse> response = cardCaptureService.doCapture(chargeId);

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CONFLICT_ERROR));
        assertThat(gatewayError.getMessage(), is("Capture for charge conflicting, 1234"));
    }

    @Test
    public void shouldUpdateChargeWithCaptureErrorWhenCaptureFails() {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);

        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findById(chargeId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge)
                .thenReturn(reloadedCharge);

        mockUnsuccessfulCapture();

        Either<GatewayError, GatewayResponse> response = cardCaptureService.doCapture(chargeId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));

        verify(reloadedCharge, times(1)).setStatus(CAPTURE_ERROR);
    }

    private void mockSuccessfulCapture() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.capture(any()))
                .thenReturn(CaptureResponse.successfulCaptureResponse(CAPTURE_SUBMITTED));
    }

    private void mockUnsuccessfulCapture() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.capture(any()))
                .thenReturn(CaptureResponse.captureFailureResponse(GatewayError.baseGatewayError("error")));
    }
}