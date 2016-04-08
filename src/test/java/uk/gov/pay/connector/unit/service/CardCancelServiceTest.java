package uk.gov.pay.connector.unit.service;

import com.google.common.util.concurrent.UncheckedExecutionException;
import fj.data.Either;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.CardCancelService;

import javax.persistence.OptimisticLockException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.ErrorType.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCancelServiceTest extends CardServiceTest {
    private final CardCancelService cardCancelService = new CardCancelService(mockedChargeDao, mockedProviders);


    @Test
    /**
     * Mocks an unsuccessful response from payment provider. We still expect charge to be successfullt cancelled
     */
    public void whenChargeThatHasStatusCreatedIsCancelled_chargeShouldBeCancelledWithoutCallingGatewayProvider() throws Exception {

        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, CREATED);
        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(reloadedCharge);

        //should not call payment provider cancel
       verifyPaymentProviderNotCalled();

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue(), is(reloadedCharge));

        verify(reloadedCharge, times(1)).setStatus(SYSTEM_CANCELLED);
    }

    @Test
    public void whenChargeThatHasStatusEnteringCardDetailsIsCancelled_chargeShouldBeCancelledWithoutCallingGatewayProvider() throws Exception {

        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(reloadedCharge);

        verifyPaymentProviderNotCalled();

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue(), is(reloadedCharge));

        verify(reloadedCharge, times(1)).setStatus(SYSTEM_CANCELLED);
    }


    @Test
    public void whenChargeThatHasAnyOtherLegalStatusIsCancelled_chargeShouldBeCancelledCallingGatewayProvider() throws Exception {

        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);
        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge)
                .thenReturn(reloadedCharge);

        mockSuccessfulCancel();

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue(), is(reloadedCharge));

        verify(reloadedCharge, times(1)).setStatus(SYSTEM_CANCELLED);
    }


    @Test
    public void shouldGetChargeNotFoundWhenChargeDoesNotExistForAccount() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        Long accountId = 1L;

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId))
                .thenReturn(Optional.empty());

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(chargeId, accountId);

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

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge);

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(ILLEGAL_STATE_ERROR));
        assertThat(gatewayError.getMessage(), is("Charge not in correct state to be processed, " + charge.getExternalId()));
    }

    @Test
    public void shouldGetAOperationAlreadyInProgressWhenStatusIsCancelReady() throws Exception {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, CANCEL_READY);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge);

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(OPERATION_ALREADY_IN_PROGRESS));
        assertThat(gatewayError.getMessage(), is("Cancellation for charge already in progress, " + charge.getExternalId()));
    }

    @Test
    public void shouldGetAConflictErrorWhenConflicting() throws Exception {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenThrow(new OptimisticLockException());

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isLeft());
        ErrorResponse gatewayError = response.left().value();

        assertThat(gatewayError.getErrorType(), is(CONFLICT_ERROR));
        assertThat(gatewayError.getMessage(), is("Operation for charge conflicting, " + charge.getExternalId()));
    }

    @Test
    public void shouldUpdateChargeWithCancelErrorWhenCancelFails() {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge)
                .thenReturn(reloadedCharge);

        mockUnsuccessfulCancel();

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));

        verify(reloadedCharge, times(1)).setStatus(CANCEL_ERROR);
    }

    void mockSuccessfulCancel() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.cancel(any()))
                .thenReturn(CancelGatewayResponse.successfulCancelResponse(SYSTEM_CANCELLED));
    }

    void mockUnsuccessfulCancel() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.cancel(any()))
                .thenReturn(CancelGatewayResponse.cancelFailureResponse(ErrorResponse.baseError("error")));
    }

    void verifyPaymentProviderNotCalled() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);

        verify(mockedPaymentProvider, never()).cancel(any());

        when(mockedPaymentProvider.cancel(any()))
                .thenReturn(CancelGatewayResponse.successfulCancelResponse(SYSTEM_CANCELLED));

    }
}