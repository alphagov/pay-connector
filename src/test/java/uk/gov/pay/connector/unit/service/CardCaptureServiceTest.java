package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.service.CardCaptureService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.GatewayErrorType.CHARGE_NOT_FOUND;
import static uk.gov.pay.connector.model.GatewayErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureServiceTest extends CardServiceTest {
    private final CardCaptureService cardService = new CardCaptureService(mockedAccountDao, mockedChargeDao, mockedProviders);

    @Test
    public void shouldCaptureACharge() throws Exception {

        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);

        when(mockedChargeDao.findById(chargeId)).thenReturn(Optional.of(charge));
        when(mockedAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));
        when(mockedProviders.resolve(providerName)).thenReturn(mockedPaymentProvider);
        CaptureResponse response1 = CaptureResponse.successfulCaptureResponse(CAPTURE_SUBMITTED);
        when(mockedPaymentProvider.capture(any())).thenReturn(response1);

        Either<GatewayError, GatewayResponse> response = cardService.doCapture(chargeId);

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
    public void shouldGetChargeNotFoundWhenChargeDoesNotExist() {

        Long chargeId = 45678L;

        when(mockedChargeDao.findById(chargeId)).thenReturn(Optional.empty());

        Either<GatewayError, GatewayResponse> response = cardService.doCapture(chargeId);

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(gatewayError.getMessage(), is("Charge with id [45678] not found."));
    }

    @Test
    public void shouldGetAnErrorWhenStatusIsNotAuthorisationSuccess() {

        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, CREATED);
        charge.setGatewayTransactionId(gatewayTxId);

        when(mockedChargeDao.findById(chargeId)).thenReturn(Optional.of(charge));

        Either<GatewayError, GatewayResponse> response = cardService.doCapture(chargeId);

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("Cannot capture a charge with status CREATED."));
    }

    @Test
    public void shouldUpdateChargeWithCaptureUnknownWhenProviderResponseIsNotSuccessful() {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);

        when(mockedChargeDao.findById(chargeId)).thenReturn(Optional.of(charge));
        when(mockedAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));
        when(mockedProviders.resolve(providerName)).thenReturn(mockedPaymentProvider);
        CaptureResponse unsuccessfulResponse = CaptureResponse.captureFailureResponse(new GatewayError("error", GENERIC_GATEWAY_ERROR));
        when(mockedPaymentProvider.capture(any())).thenReturn(unsuccessfulResponse);

        Either<GatewayError, GatewayResponse> response = cardService.doCapture(chargeId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));
        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getStatus(), is(CAPTURE_ERROR.getValue()));

        ArgumentCaptor<CaptureRequest> request = ArgumentCaptor.forClass(CaptureRequest.class);
        verify(mockedPaymentProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));
    }
}