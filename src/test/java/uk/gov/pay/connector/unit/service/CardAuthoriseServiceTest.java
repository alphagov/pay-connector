package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.ErrorType;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardAuthoriseService;
import uk.gov.pay.connector.util.CardUtils;

import javax.persistence.OptimisticLockException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.ErrorType.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardAuthoriseServiceTest extends CardServiceTest {

    private final CardAuthoriseService cardAuthorisationService = new CardAuthoriseService(mockedChargeDao, mockedProviders);

    @Test
    public void shouldAuthoriseACharge() throws Exception {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        mockSuccessfulAuthorisation(gatewayTxId);

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(gatewayTxId));
    }

    @Test
    public void shouldGetAChargeNotFoundWhenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(chargeId, CardUtils.aValidCard());

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(gatewayError.getMessage(), is("Charge with id [jgk3erq5sv2i4cds6qqa9f1a8a] not found."));
    }

    @Test
    public void shouldGetAOperationAlreadyInProgressWhenStatusIsAuthorisationReady() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.AUTHORISATION_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(OPERATION_ALREADY_IN_PROGRESS));
        assertThat(gatewayError.getMessage(), is("Authorisation for charge already in progress, " + charge.getExternalId()));
    }

    @Test
    public void shouldGetAIllegalErrorWhenInvalidStatus() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(ILLEGAL_STATE_ERROR));
        assertThat(gatewayError.getMessage(), is("Charge not in correct state to be processed, " + charge.getExternalId()));
    }

    @Test
    public void shouldGetAConflictErrorWhenConflicting() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenThrow(new OptimisticLockException());

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CONFLICT_ERROR));
        assertThat(gatewayError.getMessage(), is("Operation for charge conflicting, " + charge.getExternalId()));
    }

    @Test
    public void shouldUpdateChargeWithAuthorisationErrorWhenAuthorisationFails() {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);
        charge.setGatewayTransactionId(gatewayTxId);

        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge)
                .thenReturn(reloadedCharge);

        mockUnsuccessfulAuthorisation();

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));

        verify(reloadedCharge, times(1)).setStatus(AUTHORISATION_ERROR);
        verify(reloadedCharge, times(1)).setGatewayTransactionId(null);
    }

    private void mockSuccessfulAuthorisation(String transactionId) {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.authorise(any()))
                .thenReturn(AuthorisationResponse.successfulAuthorisationResponse(AUTHORISATION_SUCCESS, transactionId));
    }

    private void mockUnsuccessfulAuthorisation() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.authorise(any()))
                .thenReturn(AuthorisationResponse.authorisationFailureResponse(ErrorResponse.baseError("error")));
    }
}