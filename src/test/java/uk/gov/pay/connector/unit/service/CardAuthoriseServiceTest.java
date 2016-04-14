package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.AuthorisationResponse.authorisationFailureResponse;
import static uk.gov.pay.connector.model.AuthorisationResponse.successfulAuthorisationResponse;
import static uk.gov.pay.connector.model.ErrorType.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@RunWith(MockitoJUnitRunner.class)
public class CardAuthoriseServiceTest extends CardServiceTest {

    private final CardAuthoriseService cardAuthorisationService = new CardAuthoriseService(mockedChargeDao, mockedProviders, mockExecutorService);

    @Mock
    private Future<Either<ErrorResponse, GatewayResponse>> mockFutureResponse;

    @Test
    public void shouldAuthoriseACharge() throws Exception {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        Either<ErrorResponse, GatewayResponse> authorisationResponse = right(successfulAuthorisationResponse(AUTHORISATION_SUCCESS, gatewayTxId));
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(COMPLETED, authorisationResponse));

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
    }

    @Test
    public void authoriseShouldReturnInProgressWhenTimeout() throws Exception {
        Long chargeId = 1L;
        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));
        Card cardDetails = CardUtils.aValidCard();

        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isRight());
        assertTrue(response.right().value().isInProgress());
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

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        ErrorResponse mockErrorResponse = new ErrorResponse("Authorisation for charge already in progress, " + charge.getExternalId(), ErrorType.OPERATION_ALREADY_IN_PROGRESS);
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(COMPLETED, left(mockErrorResponse)));

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

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        ErrorResponse mockErrorResponse = ErrorResponse.illegalStateError("Charge not in correct state to be processed, " + charge.getExternalId());
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(COMPLETED, left(mockErrorResponse)));

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(ILLEGAL_STATE_ERROR));
        assertThat(gatewayError.getMessage(), is("Charge not in correct state to be processed, " + charge.getExternalId()));
    }

    @Test(expected=ConflictRuntimeException.class)
    public void shouldGetAConflictErrorWhenConflicting() throws Exception {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, ChargeStatus.CREATED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenThrow(new OptimisticLockException());
        when(mockExecutorService.execute(any())).thenThrow(new ConflictRuntimeException("Operation for charge conflicting, " + charge.getExternalId()));

        Card cardDetails = CardUtils.aValidCard();
        cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);
    }

    @Test
    public void shouldUpdateChargeWithAuthorisationErrorWhenAuthorisationFails() throws InterruptedException, ExecutionException, TimeoutException {
        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);
        charge.setGatewayTransactionId(gatewayTxId);
        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(charge)
                .thenReturn(reloadedCharge);
        Either<ErrorResponse, GatewayResponse> authorisationResponse = right(authorisationFailureResponse(ErrorResponse.baseError("Authorization failed")));
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(COMPLETED, authorisationResponse));

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));
    }
}