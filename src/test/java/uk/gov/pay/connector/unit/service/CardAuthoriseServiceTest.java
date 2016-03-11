package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.GatewayError;
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
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.GatewayErrorType.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class CardAuthoriseServiceTest extends CardServiceTest {

    private final CardAuthoriseService cardAuthorisationService = new CardAuthoriseService(mockedAccountDao, mockedChargeDao, mockedProviders);

    @Test
    public void shouldAuthoriseACharge() throws Exception {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";

        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findById(charge.getId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        mockSuccessfulAuthorisation(gatewayTxId);

        Card cardDetails = CardUtils.aValidCard();
        Either<GatewayError, GatewayResponse> response = cardAuthorisationService.doAuthorise(chargeId, cardDetails);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(gatewayTxId));
    }

    @Test
    public void shouldGetAChargeNotFoundWhenChargeDoesNotExist() {
        Long chargeId = 45678L;

        when(mockedChargeDao.findById(chargeId)).thenReturn(Optional.empty());

        Either<GatewayError, GatewayResponse> response = cardAuthorisationService.doAuthorise(chargeId, CardUtils.aValidCard());

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(gatewayError.getMessage(), is("Charge with id [45678] not found."));
    }

    @Test
    public void shouldGetAIllegalErrorWhenInvalidStatus() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        when(mockedChargeDao.findById(charge.getId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge);

        Card cardDetails = CardUtils.aValidCard();
        Either<GatewayError, GatewayResponse> response = cardAuthorisationService.doAuthorise(chargeId, cardDetails);

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

        Card cardDetails = CardUtils.aValidCard();
        Either<GatewayError, GatewayResponse> response = cardAuthorisationService.doAuthorise(chargeId, cardDetails);

        assertTrue(response.isLeft());
        GatewayError gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CONFLICT_ERROR));
        assertThat(gatewayError.getMessage(), is("Authorisation for charge conflicting, 1234"));
    }

    private void mockSuccessfulAuthorisation(String transactionId) {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.authorise(any()))
                .thenReturn(new AuthorisationResponse(true, null, AUTHORISATION_SUCCESS, transactionId))
        ;
    }
}