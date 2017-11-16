package uk.gov.pay.connector.service;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.worldpay.WorldpayCancelResponse;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@RunWith(MockitoJUnitRunner.class)
/**
 * PP-2626 FIXME: Cancellation and expirations statuses needs revisiting
 * For `non gateway operations` seems the statuses are not all the required ones.
 * This won't make the system broken but doing unnecessary processing.
 *
 * Spying on chargeEntity.setStatus can't be done with current implementation since,
 * TransactionContext stores instances mapped with the correspondent class, so these
 * tests are testing as blackbox unit tests (which is fine, but since it test a lot
 * of underlying code was ideal at least to make sure to get to final cancellation
 * status, needed to go through the previous ones - lock, submitted and so on).
 * A consequence of a no TDD approach
 */
public class ChargeCancelServiceTest {

    private ChargeCancelService chargeCancelService;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeEventDao mockChargeEventDao;

    @Mock
    private PaymentProviders mockPaymentProviders;

    @Mock
    private PaymentProvider mockedPaymentProvider;

    @Before
    public void setup() {
        chargeCancelService = new ChargeCancelService(mockChargeDao, mockChargeEventDao, mockPaymentProviders, TransactionFlow::new);
    }

    @Test
    public void doSystemCancel_shouldCancel_withStatusThatDoesNotNeedCancellationInGatewayProvider() throws Exception {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = 123L;
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));

        Optional<GatewayResponse<BaseCancelResponse>> response = chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().isSuccessful(), is(true));
        assertThat(chargeEntity.getStatus(), is(SYSTEM_CANCELLED.getValue()));

        verify(mockChargeEventDao).persistChargeEventOf(chargeEntity, Optional.empty());
    }

    @Test
    public void doSystemCancel_shouldCancel_havingChargeStatusThatNeedsCancellationInGatewayProvider_withCancelledGatewayResponse() throws Exception {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = 123L;
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        WorldpayCancelResponse worldpayResponse = mock(WorldpayCancelResponse.class);
        when(worldpayResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.CANCELLED);
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockChargeEventDao).persistChargeEventOf(any(ChargeEntity.class), eq(Optional.empty()));
        when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        Optional<GatewayResponse<BaseCancelResponse>> response = chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().isSuccessful(), is(true));
        assertThat(chargeEntity.getStatus(), is(SYSTEM_CANCELLED.getValue()));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId);
        verify(mockChargeDao, times(2)).findByExternalId(externalChargeId);
        verify(mockChargeEventDao, atLeastOnce()).persistChargeEventOf(argThat(chargeEntityHasStatus(SYSTEM_CANCELLED)), eq(Optional.empty()));
        verifyNoMoreInteractions(mockChargeDao);
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void doSystemCancel_shouldFail_whenChargeNotFound() throws Exception {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = 123L;

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.empty());

        chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);
    }

    @Test
    public void doUserCancel_shouldCancel_withStatusThatDoesNotNeedCancellationInGatewayProvider() throws Exception {

        String externalChargeId = "external-charge-id";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));

        Optional<GatewayResponse<BaseCancelResponse>> response = chargeCancelService.doUserCancel(externalChargeId);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().isSuccessful(), is(true));
        assertThat(chargeEntity.getStatus(), is(USER_CANCELLED.getValue()));

        verify(mockChargeEventDao).persistChargeEventOf(chargeEntity, Optional.empty());
    }

    @Test
    public void doUserCancel_shouldCancel_havingChargeStatusThatNeedsCancellationInGatewayProvider_withCancelledGatewayResponse() throws Exception {

        String externalChargeId = "external-charge-id";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        WorldpayCancelResponse worldpayResponse = mock(WorldpayCancelResponse.class);
        when(worldpayResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.CANCELLED);
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockChargeEventDao).persistChargeEventOf(any(ChargeEntity.class), eq(Optional.empty()));
        when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockedPaymentProvider);
        when(mockedPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        Optional<GatewayResponse<BaseCancelResponse>> response = chargeCancelService.doUserCancel(externalChargeId);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().isSuccessful(), is(true));
        assertThat(chargeEntity.getStatus(), is(USER_CANCELLED.getValue()));

        verify(mockChargeDao, times(3)).findByExternalId(externalChargeId);
        verify(mockChargeEventDao, atLeastOnce()).persistChargeEventOf(argThat(chargeEntityHasStatus(USER_CANCELLED)), eq(Optional.empty()));
        verifyNoMoreInteractions(mockChargeDao);
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void doUserCancel_shouldFail_whenChargeNotFound() throws Exception {

        String externalChargeId = "external-charge-id";

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.empty());

        chargeCancelService.doUserCancel(externalChargeId);
    }

    private HamcrestArgumentMatcher<ChargeEntity> chargeEntityHasStatus(ChargeStatus expectedStatus) {
        return new HamcrestArgumentMatcher<>(new TypeSafeMatcher<ChargeEntity>() {
            @Override
            protected boolean matchesSafely(ChargeEntity chargeEntity) {
                return chargeEntity.getStatus().equals(expectedStatus.getValue());
            }

            @Override
            public void describeTo(Description description) {

            }
        });
    }

    private HamcrestArgumentMatcher<CancelGatewayRequest> aCancelGatewayRequestMatching(ChargeEntity chargeEntity) {
        return new HamcrestArgumentMatcher<>(new TypeSafeMatcher<CancelGatewayRequest>() {
            @Override
            protected boolean matchesSafely(CancelGatewayRequest cancelGatewayRequest) {
                return cancelGatewayRequest.getGatewayAccount().equals(chargeEntity.getGatewayAccount()) &&
                        cancelGatewayRequest.getRequestType().equals(GatewayOperation.CANCEL) &&
                        cancelGatewayRequest.getTransactionId().equals(chargeEntity.getGatewayTransactionId());
            }

            @Override
            public void describeTo(Description description) {

            }
        });
    }

}
