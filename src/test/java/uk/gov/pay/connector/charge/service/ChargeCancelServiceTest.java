package uk.gov.pay.connector.charge.service;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.transaction.TransactionFlow;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCancelResponse;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.smartpay.SmartpayCancelResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;

import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

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
    private PaymentProvider mockPaymentProvider;

    @Before
    public void setup() {
        chargeCancelService = new ChargeCancelService(mockChargeDao, mockChargeEventDao, mockPaymentProviders, TransactionFlow::new);
    }

    @Test
    public void doSystemCancel_shouldCancel_withStatusThatDoesNotNeedCancellationInGatewayProvider() throws Exception {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
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
        Long gatewayAccountId = nextLong();
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
        when(mockPaymentProviders.getPaymentProviderFor(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

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
        Long gatewayAccountId = nextLong();

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
        when(mockPaymentProviders.getPaymentProviderFor(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

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

    @Test
    public void doSystemCancel_shouldCancelWorldPayCharge_withStatus_awaitingCaptureRequest() {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .build();

        WorldpayCancelResponse worldpayResponse = mock(WorldpayCancelResponse.class);
        when(worldpayResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.CANCELLED);
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockChargeEventDao).persistChargeEventOf(any(ChargeEntity.class), eq(Optional.empty()));
        when(mockPaymentProviders.getPaymentProviderFor(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        Optional<GatewayResponse<BaseCancelResponse>> response = chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().isSuccessful(), is(true));
        assertThat(chargeEntity.getStatus(), is(SYSTEM_CANCELLED.getValue()));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId);
        verify(mockChargeDao, times(2)).findByExternalId(externalChargeId);
        verify(mockChargeEventDao, atLeastOnce()).persistChargeEventOf(argThat(chargeEntityHasStatus(SYSTEM_CANCELLED)), eq(Optional.empty()));

        verifyNoMoreInteractions(mockChargeDao);
    }

    @Test
    public void doSystemCancel_shouldCancelSmartPayCharge_withStatus_awaitingCaptureRequest() {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .build();

        SmartpayCancelResponse smartpayCancelResponse = mock(SmartpayCancelResponse.class);
        when(smartpayCancelResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.CANCELLED);
        GatewayResponse.GatewayResponseBuilder<SmartpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(smartpayCancelResponse).build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockChargeEventDao).persistChargeEventOf(any(ChargeEntity.class), eq(Optional.empty()));
        when(mockPaymentProviders.getPaymentProviderFor(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        Optional<GatewayResponse<BaseCancelResponse>> response = chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().isSuccessful(), is(true));
        assertThat(chargeEntity.getStatus(), is(SYSTEM_CANCELLED.getValue()));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId);
        verify(mockChargeDao, times(2)).findByExternalId(externalChargeId);
        verify(mockChargeEventDao, atLeastOnce()).persistChargeEventOf(argThat(chargeEntityHasStatus(SYSTEM_CANCELLED)), eq(Optional.empty()));

        verifyNoMoreInteractions(mockChargeDao);
    }

    @Test
    public void doSystemCancel_shouldCancelEPDQCharge_withStatus_awaitingCaptureRequest() {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .build();

        EpdqCancelResponse epdqCancelResponse = mock(EpdqCancelResponse.class);
        when(epdqCancelResponse.cancelStatus()).thenReturn(BaseCancelResponse.CancelStatus.CANCELLED);
        GatewayResponse.GatewayResponseBuilder<EpdqCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(epdqCancelResponse).build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));
        doNothing().when(mockChargeEventDao).persistChargeEventOf(any(ChargeEntity.class), eq(Optional.empty()));
        when(mockPaymentProviders.getPaymentProviderFor(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        Optional<GatewayResponse<BaseCancelResponse>> response = chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        assertThat(response.isPresent(), is(true));
        assertThat(response.get().isSuccessful(), is(true));
        assertThat(chargeEntity.getStatus(), is(SYSTEM_CANCELLED.getValue()));

        verify(mockChargeDao).findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId);
        verify(mockChargeDao, times(2)).findByExternalId(externalChargeId);
        verify(mockChargeEventDao, atLeastOnce()).persistChargeEventOf(argThat(chargeEntityHasStatus(SYSTEM_CANCELLED)), eq(Optional.empty()));

        verifyNoMoreInteractions(mockChargeDao);
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
