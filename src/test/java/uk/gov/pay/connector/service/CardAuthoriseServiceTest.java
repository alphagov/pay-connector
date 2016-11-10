package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.util.CardUtils;

import javax.persistence.OptimisticLockException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

@RunWith(MockitoJUnitRunner.class)
public class CardAuthoriseServiceTest extends CardServiceTest {

    private final CardAuthoriseService cardAuthorisationService = new CardAuthoriseService(mockedChargeDao, mockedProviders, mockExecutorService);

    @Mock
    private Future<Either<Error, GatewayResponse>> mockFutureResponse;

    public void setupMockExecutorServiceMock() {
        doAnswer(invocation -> Pair.of(COMPLETED, ((Supplier) invocation.getArguments()[0]).get()))
                .when(mockExecutorService).execute(any(Supplier.class));
    }

    public void setupPaymentProviderMock(String transactionId, boolean isAuthorised, String errorCode) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        when(worldpayResponse.isAuthorised()).thenReturn(isAuthorised);
        when(worldpayResponse.getErrorCode()).thenReturn(errorCode);
        GatewayResponse authorisationResponse = GatewayResponse.with(worldpayResponse);
        when(mockedPaymentProvider.authorise(any())).thenReturn(authorisationResponse);
    }

    @Test
    public void shouldRespondAuthorisationSuccess() throws Exception {
        String transactionId = "transaction-id";
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);
        Card cardDetails = CardUtils.aValidCard();

        GatewayResponse response = anAuthorisationSuccessResponse(charge, reloadedCharge, transactionId, cardDetails);

        assertThat(response.isSuccessful(), is(true));
        verify(reloadedCharge).setStatus(AUTHORISATION_SUCCESS);
        verify(reloadedCharge).setGatewayTransactionId(transactionId);
        verify(reloadedCharge).setCardDetails(any(CardDetailsEntity.class));
    }

    @Test
    public void shouldRespondAuthorisationSuccess_whenTransactionIdIsGenerated() throws Exception {
        String generatedTransactionId = "generated-transaction-id";
        String providerTransactionId = "provider-transaction-id";
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(reloadedCharge).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(providerTransactionId, true, null);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.of(generatedTransactionId));

        GatewayResponse response = cardAuthorisationService.doAuthorise(charge.getExternalId(), CardUtils.aValidCard());

        assertThat(response.isSuccessful(), is(true));
        verify(reloadedCharge).setStatus(AUTHORISATION_SUCCESS);
        InOrder inOrder = Mockito.inOrder(reloadedCharge);
        inOrder.verify(reloadedCharge).setGatewayTransactionId(generatedTransactionId);
        inOrder.verify(reloadedCharge).setGatewayTransactionId(providerTransactionId);
    }

    @Test
    public void shouldRespondAuthorisationRejected() throws Exception {
        String transactionId = "transaction-id";
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);
        GatewayResponse response = anAuthorisationRejectedResponse(charge, reloadedCharge);

        assertThat(response.isSuccessful(), is(true));
        verify(reloadedCharge).setStatus(AUTHORISATION_REJECTED);
        verify(reloadedCharge).setGatewayTransactionId(transactionId);
    }

    @Test
    public void shouldRespondAuthorisationError() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);

        GatewayResponse response = anAuthorisationErrorResponse(charge, reloadedCharge);
        assertThat(response.isFailed(), is(true));
        verify(reloadedCharge).setStatus(AUTHORISATION_ERROR);
        verify(reloadedCharge, never()).setGatewayTransactionId(any());
    }

    @Test
    public void shouldStoreConfirmationDetailsIfAuthorisationSuccess() {
        String transactionId = "transaction-id";
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);
        Card cardDetails = CardUtils.aValidCard();
        anAuthorisationSuccessResponse(charge, reloadedCharge, transactionId, cardDetails);

        verify(reloadedCharge, times(1)).setCardDetails(any(CardDetailsEntity.class));
    }

    @Test
    public void shouldStoreConfirmationDetailsEvenIfAuthorisationRejected() {
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);

        anAuthorisationRejectedResponse(charge, reloadedCharge);
        verify(reloadedCharge, times(1)).setCardDetails(any(CardDetailsEntity.class));
    }

    @Test
    public void shouldStoreConfirmationDetailsEvenIfInAuthorisationError() {
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = spy(charge);

        anAuthorisationErrorResponse(charge, reloadedCharge);
        verify(reloadedCharge, times(1)).setCardDetails(any(CardDetailsEntity.class));
    }

    @Test
    public void authoriseShouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenTimeout() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);
        when(mockExecutorService.execute(any())).thenReturn(Pair.of(IN_PROGRESS, null));

        try {
            cardAuthorisationService.doAuthorise(charge.getExternalId(), CardUtils.aValidCard());
            fail("Exception not thrown.");
        } catch (OperationAlreadyInProgressRuntimeException e) {
            Map<String, String> expectedMessage = ImmutableMap.of("message", format("Authorisation for charge already in progress, %s", charge.getExternalId()));
            assertThat(e.getResponse().getEntity(), is(expectedMessage));
        }
    }
    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowAChargeNotFoundRuntimeExceptionWhenChargeDoesNotExist() {
        String chargeId = "jgk3erq5sv2i4cds6qqa9f1a8a";

        when(mockedChargeDao.findByExternalId(chargeId))
                .thenReturn(Optional.empty());

        cardAuthorisationService.doAuthorise(chargeId, CardUtils.aValidCard());
    }

    @Test(expected = OperationAlreadyInProgressRuntimeException.class)
    public void shouldThrowAnOperationAlreadyInProgressRuntimeExceptionWhenStatusIsAuthorisationReady() {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.AUTHORISATION_READY);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);

        setupMockExecutorServiceMock();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), CardUtils.aValidCard());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnIllegalStateRuntimeExceptionWhenInvalidStatus() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, ChargeStatus.CREATED);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(charge);

        setupMockExecutorServiceMock();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), CardUtils.aValidCard());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    @Test(expected = ConflictRuntimeException.class)
    public void shouldThrowAConflictRuntimeException() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, ENTERING_CARD_DETAILS);

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenThrow(new OptimisticLockException());

        setupMockExecutorServiceMock();

        cardAuthorisationService.doAuthorise(charge.getExternalId(), CardUtils.aValidCard());
        verifyNoMoreInteractions(mockedChargeDao, mockedProviders);
    }

    private GatewayResponse anAuthorisationRejectedResponse(ChargeEntity charge, ChargeEntity reloadedCharge) {
        String transactionId = "transaction-id";

        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(reloadedCharge).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, false, null);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return cardAuthorisationService.doAuthorise(charge.getExternalId(), CardUtils.aValidCard());
    }

    private GatewayResponse anAuthorisationSuccessResponse(ChargeEntity charge, ChargeEntity reloadedCharge, String transactionId, Card cardDetails) {

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any()))
                .thenReturn(reloadedCharge)
                .thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(transactionId, true, null);

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return cardAuthorisationService.doAuthorise(charge.getExternalId(), cardDetails);
    }

    private GatewayResponse anAuthorisationErrorResponse(ChargeEntity charge, ChargeEntity reloadedCharge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId())).thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(any())).thenReturn(reloadedCharge).thenReturn(reloadedCharge);

        setupMockExecutorServiceMock();
        setupPaymentProviderMock(null, false, "error-code");

        when(mockedProviders.byName(charge.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.generateTransactionId()).thenReturn(Optional.empty());

        return cardAuthorisationService.doAuthorise(charge.getExternalId(), CardUtils.aValidCard());
    }
}
