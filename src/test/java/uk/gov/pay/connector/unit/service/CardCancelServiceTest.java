package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.CardCancelService;
import uk.gov.pay.connector.service.ChargeService;

import javax.persistence.OptimisticLockException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardCancelService.EXPIRY_FAILED;
import static uk.gov.pay.connector.service.CardCancelService.EXPIRY_SUCCESS;

public class CardCancelServiceTest extends CardServiceTest {
    @Mock
    protected ChargeService chargeService = mock(ChargeService.class);

    private final CardCancelService cardCancelService = new CardCancelService(mockedChargeDao, mockedProviders, chargeService);

    private final Long chargeId = 1234L;
    private final Long accountId = 1L;

    private static ChargeStatus[] nonGatewayStatuses = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS
    };

    @Test
    public void whenChargeThatHasStatusCreatedIsCancelled_chargeShouldBeCancelledWithoutCallingGatewayProvider() throws Exception {
        ChargeEntity charge = createNewChargeWith(chargeId, CREATED);

        mockChargeDaoFindByChargeIdAndAccountId(charge, accountId);
        verifyPaymentProviderNotCalled();

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        verifyChargeUpdated(charge, SYSTEM_CANCELLED);
    }

    @Test
    public void whenChargeThatHasStatusEnteringCardDetailsIsCancelled_chargeShouldBeCancelledWithoutCallingGatewayProvider() throws Exception {
        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);

        mockChargeDaoFindByChargeIdAndAccountId(charge, accountId);
        verifyPaymentProviderNotCalled();

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        verifyChargeUpdated(charge, SYSTEM_CANCELLED);
    }


    @Test
    public void whenChargeThatHasAnyOtherLegalStatusIsCancelled_chargeShouldBeCancelledCallingGatewayProvider() throws Exception {
        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        mockChargeDaoMergeCharge(charge);
        mockChargeDaoFindByChargeIdAndAccountId(charge, accountId);

        mockSuccessfulCancel();

        Either<ErrorResponse, GatewayResponse> response = cardCancelService.doCancel(charge.getExternalId(), accountId);

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        verifyChargeUpdated(charge, SYSTEM_CANCELLED);
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldGetChargeNotFoundWhenChargeDoesNotExistForAccount() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";
        Long accountId = 1L;

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId))
                .thenReturn(Optional.empty());

        cardCancelService.doCancel(chargeId, accountId);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldFailForStatesThatAreNotCancellable() {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, CAPTURE_SUBMITTED);
        charge.setId(chargeId);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge);

        cardCancelService.doCancel(charge.getExternalId(), accountId);
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldGetAOperationAlreadyInProgressWhenStatusIsCancelReady() throws Exception {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, CANCEL_READY);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge);

        cardCancelService.doCancel(charge.getExternalId(), accountId);
    }

    @Test(expected=ConflictRuntimeException.class)
    public void shouldGetAConflictErrorWhenConflicting() throws Exception {
        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenThrow(new OptimisticLockException());

        cardCancelService.doCancel(charge.getExternalId(), accountId);
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

        verifyChargeUpdated(charge, CANCEL_ERROR);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithSuccessfulProviderCancellation() {
        ChargeEntity chargeEntity1 = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);
        ChargeEntity chargeEntity2 = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        chargeEntity1.setGatewayAccount(gatewayAccount);
        chargeEntity2.setGatewayAccount(gatewayAccount);


        when(gatewayAccount.getId()).thenReturn(accountId);
        when(gatewayAccount.getGatewayName()).thenReturn(providerName);

        mockChargeDaoFindByChargeIdAndAccountId(chargeEntity1, accountId);
        mockChargeDaoFindByChargeIdAndAccountId(chargeEntity2, accountId);
        mockChargeDaoMergeCharge(chargeEntity1);
        mockChargeDaoMergeCharge(chargeEntity2);

        mockSuccessfulCancel();
        Map<String, Integer> result = cardCancelService.expire(asList(chargeEntity1, chargeEntity2));
        assertEquals(2, result.get(EXPIRY_SUCCESS).intValue());
        assertEquals(0, result.get(EXPIRY_FAILED).intValue());

        InOrder inOrder = inOrder(chargeService, chargeService, chargeService);
        inOrder.verify(chargeService).updateStatus(Arrays.asList(chargeEntity1), EXPIRED);
        inOrder.verify(chargeService).updateStatus(Arrays.asList(chargeEntity2), EXPIRE_CANCEL_PENDING);
        inOrder.verify(chargeService).updateStatus(Arrays.asList(chargeEntity2), EXPIRED);
    }

    @Test
    public void shouldUpdateChargeStatusWhenExpiringWithFailedProviderCancellation() {
        ChargeStatus[] legalStatuses = new ChargeStatus[]{
                CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_READY, CAPTURE_READY
        };
        ChargeEntity chargeEntity1 = mock(ChargeEntity.class);
        ChargeEntity chargeEntity2 = mock(ChargeEntity.class);
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        when(gatewayAccount.getId()).thenReturn(accountId);
        when(gatewayAccount.getGatewayName()).thenReturn(providerName);

        when(chargeEntity2.getGatewayAccount()).thenReturn(gatewayAccount);
        when(chargeEntity1.getGatewayAccount()).thenReturn(gatewayAccount);

        when(chargeEntity1.getStatus()).thenReturn(ChargeStatus.ENTERING_CARD_DETAILS.getValue());
        when(chargeEntity2.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());
        when(chargeEntity2.hasStatus(legalStatuses)).thenReturn(true);

        mockChargeDaoFindByChargeIdAndAccountId(chargeEntity1, accountId);
        mockChargeDaoFindByChargeIdAndAccountId(chargeEntity2, accountId);
        mockChargeDaoMergeCharge(chargeEntity1);
        mockChargeDaoMergeCharge(chargeEntity2);


        mockUnsuccessfulCancel();

        Map<String, Integer> result = cardCancelService.expire(asList(chargeEntity1, chargeEntity2));
        assertEquals(1, result.get(EXPIRY_SUCCESS).intValue());
        assertEquals(1, result.get(EXPIRY_FAILED).intValue());

        InOrder inOrder = inOrder(chargeService, chargeService, chargeService);
        inOrder.verify(chargeService).updateStatus(Arrays.asList(chargeEntity1), EXPIRED);
        inOrder.verify(chargeService).updateStatus(Arrays.asList(chargeEntity2), EXPIRE_CANCEL_PENDING);
        inOrder.verify(chargeService).updateStatus(Arrays.asList(chargeEntity2), EXPIRE_CANCEL_FAILED);
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

    void verifyChargeUpdated(ChargeEntity charge, ChargeStatus status) {
        verify(chargeService).updateStatus(Arrays.asList(charge), status);
    }

    void verifyPaymentProviderNotCalled() {
        when(mockedProviders.resolve(providerName))
                .thenReturn(mockedPaymentProvider);

        verify(mockedPaymentProvider, never()).cancel(any());
    }

    void mockChargeDaoFindByChargeIdAndAccountId(ChargeEntity charge, Long accountId) {
        when(mockedChargeDao.findByExternalIdAndGatewayAccount(charge.getExternalId(), accountId))
                .thenReturn(Optional.of(charge));
    }

    void mockChargeDaoMergeCharge(ChargeEntity charge) {
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge);
    }
}