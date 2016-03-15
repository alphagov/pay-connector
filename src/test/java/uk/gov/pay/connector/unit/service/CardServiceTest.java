package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.fixture.ChargeEntityFixture;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.service.CardService;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.util.CardUtils;

import javax.persistence.OptimisticLockException;
import java.util.Optional;

import static org.assertj.core.util.Maps.newHashMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.ErrorType.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardServiceTest {
    private final String providerName = "theProvider";

    private final PaymentProvider theMockProvider = mock(PaymentProvider.class);
    private GatewayAccountDao mockAccountDao = mock(GatewayAccountDao.class);
    private ChargeDao mockChargeDao = mock(ChargeDao.class);
    private PaymentProviders mockProviders = mock(PaymentProviders.class);

    private final CardService cardService = new CardService(mockAccountDao, mockChargeDao, mockProviders);

    @Test
    public void doAuthorise_shouldAuthoriseACharge() throws Exception {

        Long chargeId = 1L;
        String gatewayTxId = "theTxId";

        ChargeEntity charge = newCharge(chargeId, ENTERING_CARD_DETAILS);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalId(externalId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(any()))
                .thenReturn(charge)
                .thenReturn(charge);

        mockSuccessfulAuthorisation(gatewayTxId);

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardService.doAuthorise(externalId, cardDetails);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        assertThat(charge.getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.getGatewayTransactionId(), is(gatewayTxId));
    }

    @Test
    public void doAuthorise_shouldGetAChargeNotFoundWhenChargeDoesNotExist() {

        String chargeId = "45678";

        when(mockChargeDao.findByExternalId(chargeId)).thenReturn(Optional.empty());

        Either<ErrorResponse, GatewayResponse> response = cardService.doAuthorise(chargeId, CardUtils.aValidCard());

        assertTrue(response.isLeft());
        ErrorResponse errorResponse = response.left().value();

        assertThat(errorResponse.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(errorResponse.getMessage(), is("Charge with id [45678] not found."));
    }

    @Test
    public void doAuthorise_shouldGetAIllegalErrorWhenInvalidStatus() throws Exception {

        Long chargeId = 1234L;

        ChargeEntity charge = newCharge(chargeId, ChargeStatus.CREATED);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalId(externalId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(any()))
                .thenReturn(charge)
                .thenReturn(charge);

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardService.doAuthorise(externalId, cardDetails);

        assertTrue(response.isLeft());
        ErrorResponse errorResponse = response.left().value();

        assertThat(errorResponse.getErrorType(), is(ILLEGAL_STATE_ERROR));
        assertThat(errorResponse.getMessage(), is("Charge not in correct state to be processed, " + externalId));
    }

    @Test
    public void doAuthorise_shouldGetAConflictErrorWhenConflicting() throws Exception {

        Long chargeId = 1234L;

        ChargeEntity charge = newCharge(chargeId, ChargeStatus.CREATED);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalId(externalId))
                .thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(any()))
                .thenThrow(new OptimisticLockException());

        when(mockChargeDao.findById(chargeId)).thenReturn(Optional.of(charge));

        Card cardDetails = CardUtils.aValidCard();
        Either<ErrorResponse, GatewayResponse> response = cardService.doAuthorise(externalId, cardDetails);

        assertTrue(response.isLeft());
        ErrorResponse errorResponse = response.left().value();

        assertThat(errorResponse.getErrorType(), is(CONFLICT_ERROR));
        assertThat(errorResponse.getMessage(), is("Authorisation for charge conflicting, " + externalId));
    }

    @Test
    public void doCapture_shouldCaptureACharge() throws Exception {

        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = newCharge(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalId(externalId)).thenReturn(Optional.of(charge));
        when(mockAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));
        when(mockProviders.resolve(providerName)).thenReturn(theMockProvider);
        CaptureResponse response1 = new CaptureResponse(true, null);
        when(theMockProvider.capture(any())).thenReturn(response1);

        Either<ErrorResponse, GatewayResponse> response = cardService.doCapture(externalId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getStatus(), is(CAPTURE_SUBMITTED.getValue()));

        ArgumentCaptor<CaptureRequest> request = ArgumentCaptor.forClass(CaptureRequest.class);
        verify(theMockProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));
    }

    @Test
    public void doCapture_shouldGetChargeNotFoundWhenChargeDoesNotExist() {

        String chargeId = "45678abc";

        when(mockChargeDao.findByExternalId(chargeId)).thenReturn(Optional.empty());

        Either<ErrorResponse, GatewayResponse> response = cardService.doCapture(chargeId);

        assertTrue(response.isLeft());
        ErrorResponse errorResponse = response.left().value();

        assertThat(errorResponse.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(errorResponse.getMessage(), is("Charge with id [" + chargeId + "] not found."));
    }

    @Test
    public void doCapture_ShouldGetAnErrorWhenStatusIsNotAuthorisationSuccess() {

        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = newCharge(chargeId, CREATED);
        charge.setGatewayTransactionId(gatewayTxId);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalId(externalId)).thenReturn(Optional.of(charge));

        Either<ErrorResponse, GatewayResponse> response = cardService.doCapture(externalId);

        assertTrue(response.isLeft());
        ErrorResponse errorResponse = response.left().value();

        assertThat(errorResponse.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(errorResponse.getMessage(), is("Cannot capture a charge with status CREATED."));
    }

    @Test
    public void doCapture_shouldUpdateChargeWithCaptureUnknownWhenProviderResponseIsNotSuccessful() {

        Long chargeId = 1L;
        String gatewayTxId = "theTxId";
        ChargeEntity charge = newCharge(chargeId, AUTHORISATION_SUCCESS);
        charge.setGatewayTransactionId(gatewayTxId);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalId(externalId)).thenReturn(Optional.of(charge));
        when(mockAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));
        when(mockProviders.resolve(providerName)).thenReturn(theMockProvider);
        CaptureResponse unsuccessfulResponse = new CaptureResponse(false, new ErrorResponse("error", GENERIC_GATEWAY_ERROR));
        when(theMockProvider.capture(any())).thenReturn(unsuccessfulResponse);

        Either<ErrorResponse, GatewayResponse> response = cardService.doCapture(externalId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));
        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getStatus(), is(CAPTURE_UNKNOWN.getValue()));

        ArgumentCaptor<CaptureRequest> request = ArgumentCaptor.forClass(CaptureRequest.class);
        verify(theMockProvider, times(1)).capture(request.capture());
        assertThat(request.getValue().getTransactionId(), is(gatewayTxId));
    }

    @Test
    public void doCancel_shouldCancelACharge() throws Exception {

        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = newCharge(chargeId, ENTERING_CARD_DETAILS);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(Optional.of(charge));
        when(mockAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));

        when(mockProviders.resolve(providerName)).thenReturn(theMockProvider);
        CancelResponse cancelResponse = new CancelResponse(true, null);
        when(theMockProvider.cancel(any())).thenReturn(cancelResponse);

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(externalId, accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);

        verify(mockChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());
        ChargeEntity updatedCharge = argumentCaptor.getValue();
        assertThat(updatedCharge.getStatus(), is(SYSTEM_CANCELLED.getValue()));
    }

    @Test
    public void doCancel_shouldGetChargeNotFoundWhenChargeDoesNotExistForAccount() {
        String chargeId = "1234";
        Long accountId = 1L;

        when(mockChargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)).thenReturn(Optional.empty());

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(chargeId, accountId);

        assertTrue(response.isLeft());
        ErrorResponse errorResponse = response.left().value();

        assertThat(errorResponse.getErrorType(), is(CHARGE_NOT_FOUND));
        assertThat(errorResponse.getMessage(), is("Charge with id [1234] not found."));
    }

    @Test
    public void doCancel_shouldFailForStatesThatAreNotCancellable() {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = newCharge(chargeId, CAPTURE_SUBMITTED);
        charge.setId(chargeId);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(Optional.of(charge));

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(externalId, accountId);

        assertTrue(response.isLeft());
        ErrorResponse errorResponse = response.left().value();

        assertThat(errorResponse.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(errorResponse.getMessage(), is("Cannot cancel a charge id [" + externalId + "]: status is [CAPTURE SUBMITTED]."));
    }

    @Test
    public void doCancel_shouldNotUpdateStatusWhenProviderResponseIsNotSuccessful() {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = newCharge(chargeId, ENTERING_CARD_DETAILS);

        String externalId = charge.getExternalId();
        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(Optional.of(charge));
        when(mockAccountDao.findById(charge.getGatewayAccount().getId())).thenReturn(Optional.of(charge.getGatewayAccount()));

        when(mockProviders.resolve(providerName)).thenReturn(theMockProvider);
        CancelResponse cancelResponse = new CancelResponse(false, new ErrorResponse("Error", ErrorType.GENERIC_GATEWAY_ERROR));
        when(theMockProvider.cancel(any())).thenReturn(cancelResponse);

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(externalId, accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));

        verify(mockChargeDao, never()).merge(any(ChargeEntity.class));

    }

    @Test
    public void doAuthorise_shouldReturnChargeExpiredErrorTypeForExpiredCharges() {
        ChargeEntity charge = newCharge(100L, EXPIRED);
        String externalId = charge.getExternalId();
        when(mockChargeDao.findById(100L)).thenReturn(Optional.of(charge));
        when(mockChargeDao.merge(any())).thenReturn(charge);

        CardService cardService = new CardService(mockAccountDao, mockChargeDao, mockProviders);
        Either<ErrorResponse, GatewayResponse> response = cardService.doAuthorise(externalId, new Card());

        assertTrue(response.isLeft());
        assertEquals(response.left().value().getErrorType(), ErrorType.CHARGE_EXPIRED);
    }

    @Test
    public void doCapture_shouldReturnChargeExpiredErrorTypeForExpiredCharges() {
        long chargeId = 100L;
        ChargeEntity charge = newCharge(chargeId, EXPIRED);
        String externalId = charge.getExternalId();
        when(mockChargeDao.findById(chargeId)).thenReturn(Optional.of(charge));

        Either<ErrorResponse, GatewayResponse> response = cardService.doCapture(externalId);

        assertTrue(response.isLeft());
        assertEquals(response.left().value().getErrorType(), ErrorType.CHARGE_EXPIRED);
    }

    @Test
    public void doCancel_shouldReturnChargeExpiredErrorTypeForExpiredCharges() {
        long chargeId = 100L;
        ChargeEntity charge = newCharge(chargeId, EXPIRED);
        String externalId = charge.getExternalId();
        long accountId = 1L;

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalId, accountId)).thenReturn(Optional.of(charge));

        Either<ErrorResponse, GatewayResponse> response = cardService.doCancel(externalId, accountId);

        assertTrue(response.isLeft());
        assertEquals(response.left().value().getErrorType(), ErrorType.CHARGE_EXPIRED);
    }

    private void mockSuccessfulAuthorisation(String transactionId) {
        when(mockProviders.resolve(providerName)).thenReturn(theMockProvider);
        AuthorisationResponse resp = new AuthorisationResponse(true, null, AUTHORISATION_SUCCESS, transactionId);
        when(theMockProvider.authorise(any())).thenReturn(resp);
    }

    private GatewayAccount newAccount() {
        return new GatewayAccount(RandomUtils.nextLong(), providerName, newHashMap());
    }

    private ChargeEntity newCharge(Long chargeId, ChargeStatus status) {
        GatewayAccount gatewayAccount = newAccount();
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(gatewayAccount.getGatewayName(), gatewayAccount.getCredentials());
        gatewayAccountEntity.setId(gatewayAccount.getId());
        return ChargeEntityFixture.aValidChargeEntity()
                .withId(chargeId)
                .withStatus(status)
                .withGatewayAccountEntity(gatewayAccountEntity).build();
    }

    private Matcher<GatewayResponse> aSuccessfulResponse() {
        return new TypeSafeMatcher<GatewayResponse>() {
            private GatewayResponse gatewayResponse;

            @Override
            protected boolean matchesSafely(GatewayResponse gatewayResponse) {
                this.gatewayResponse = gatewayResponse;
                return gatewayResponse.isSuccessful() && gatewayResponse.getError() == null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Success, but response was not successful: " + gatewayResponse.getError().getMessage());
            }
        };
    }

    private Matcher<GatewayResponse> anUnSuccessfulResponse() {
        return new TypeSafeMatcher<GatewayResponse>() {
            private GatewayResponse gatewayResponse;

            @Override
            protected boolean matchesSafely(GatewayResponse gatewayResponse) {
                this.gatewayResponse = gatewayResponse;
                return !gatewayResponse.isSuccessful() && gatewayResponse.getError() != null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Response Error : " + gatewayResponse.getError().getMessage());
            }
        };
    }

}