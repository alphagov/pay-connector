package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.model.CancelResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.ErrorType;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.service.CardCancelService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.ErrorType.CHARGE_NOT_FOUND;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCancelServiceTest extends CardServiceTest {
    private final CardCancelService cardService = new CardCancelService(mockedAccountDao, mockedChargeDao, mockedProviders);

    @Test
    public void shouldCancelACharge() throws Exception {

        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId)).thenReturn(Optional.of(charge));
        when(mockedAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));

        when(mockedProviders.resolve(providerName)).thenReturn(mockedPaymentProvider);
        CancelResponse cancelResponse = new CancelResponse(true, null);
        when(mockedPaymentProvider.cancel(any())).thenReturn(cancelResponse);

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());
        ChargeEntity updatedCharge = argumentCaptor.getValue();
        assertThat(updatedCharge.getStatus(), is(SYSTEM_CANCELLED.getValue()));
    }

    @Test
    public void shouldGetChargeNotFoundWhenChargeDoesNotExistForAccount() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        Long accountId = 1L;

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)).thenReturn(Optional.empty());

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(chargeId, accountId);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(gatewayError.getMessage(), is("Charge with id [jgk3erq5sv2i4cds6qqa9f1a8a] not found."));
    }

    @Test
    public void shouldFailForStatesThatAreNotCancellable() {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, CAPTURE_SUBMITTED);
        charge.setId(chargeId);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId)).thenReturn(Optional.of(charge));

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is(String.format("Cannot cancel a charge id [%s]: status is [CAPTURE SUBMITTED].", charge.getExternalId())));
    }

    @Test
    public void shouldNotUpdateStatusWhenProviderResponseIsNotSuccessful() {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId)).thenReturn(Optional.of(charge));
        when(mockedAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));

        when(mockedProviders.resolve(providerName)).thenReturn(mockedPaymentProvider);
        CancelResponse cancelResponse = new CancelResponse(false, new ErrorResponse("Error", ErrorType.GENERIC_GATEWAY_ERROR));
        when(mockedPaymentProvider.cancel(any())).thenReturn(cancelResponse);

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));

        verify(mockedChargeDao, never()).merge(any(ChargeEntity.class));

    }
}