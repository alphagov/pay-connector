package uk.gov.pay.connector.charge.service;


import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.hamcrest.HamcrestArgumentMatcher;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCancelResponse;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;

import java.util.Optional;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@ExtendWith(MockitoExtension.class)
class ChargeCancelServiceTest {


    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private PaymentProviders mockPaymentProviders;

    @Mock
    private PaymentProvider mockPaymentProvider;

    @Mock
    private ChargeService chargeService;

    @Mock
    private QueryService mockQueryService;

    @InjectMocks
    private ChargeCancelService chargeCancelService;

    @Test
   void doSystemCancel_shouldCancel_withStatusThatDoesNotNeedCancellationInGatewayProvider() {
        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));

        chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        verify(chargeService).transitionChargeState(externalChargeId, SYSTEM_CANCELLED);
    }
    @ParameterizedTest
    @ValueSource( strings = {
            "AUTHORISATION 3DS REQUIRED",
            "AUTHORISATION 3DS READY",
            "AUTHORISATION READY"
    })
    void doSystemCancel_chargeStatusDuringAuthorisation_DoesNotNeedCancellationWithProvider(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(status)
                .build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));

        chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        verify(mockPaymentProvider, never()).cancel(any());
        verify(chargeService).transitionChargeState(externalChargeId, SYSTEM_CANCELLED);
    }


    @ParameterizedTest
    @ValueSource( strings = {
            "AUTHORISATION 3DS REQUIRED",
            "AUTHORISATION 3DS READY",
            "AUTHORISATION SUCCESS"
    })
    public void doSystemCancel_chargeStatusDuringAuthorisation_needsCancellationWithProvider(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(status)
                .build();

        WorldpayCancelResponse worldpayResponse = new WorldpayCancelResponse();
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        if ("AUTHORISATION SUCCESS".equals(chargeStatus)) {
            when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);
            when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        }

        chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);
        if ("AUTHORISATION SUCCESS".equals(chargeStatus)) {
            verify(mockPaymentProvider).cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)));
        }
        verify(chargeService).transitionChargeState(externalChargeId, SYSTEM_CANCELLED);
    }

    @Test
    void doSystemCancel_chargeStatusAfterAuthorisation_cancelledWithProvider() throws Exception {
        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        WorldpayCancelResponse worldpayResponse = new WorldpayCancelResponse();
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        verify(mockPaymentProvider).cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)));
        verify(chargeService).transitionChargeState(externalChargeId, SYSTEM_CANCELLED);
        verifyNoMoreInteractions(ignoreStubs(mockChargeDao));
    }

    @Test
    void doSystemCancel_shouldFail_whenChargeNotFound() {

        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.empty());

        final Optional<ChargeEntity> maybeCharge = chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);
        assertThat(maybeCharge.isPresent(), is(false));
    }

    @Test
    void doUserCancel_shouldCancel_withStatusThatDoesNotNeedCancellationInGatewayProvider() {
        String externalChargeId = "external-charge-id";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .build();

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));

        chargeCancelService.doUserCancel(externalChargeId);

        verify(chargeService).transitionChargeState(externalChargeId, USER_CANCELLED);
    }


    @ParameterizedTest
    @ValueSource( strings = {
            "AUTHORISATION 3DS REQUIRED",
            "AUTHORISATION 3DS READY",
            "AUTHORISATION READY"
    })
    void doUserCancel_chargeStatusDuringAuthorisation_DoesNotNeedCancellationWithProvider(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);

        String externalChargeId = "external-charge-id";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(status)
                .build();

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));

        chargeCancelService.doUserCancel(externalChargeId);

        verify(mockPaymentProvider, never()).cancel(any());
        verify(chargeService).transitionChargeState(externalChargeId, USER_CANCELLED);
    }


    @ParameterizedTest
    @ValueSource( strings = {
            "AUTHORISATION 3DS REQUIRED",
            "AUTHORISATION 3DS READY",
            "AUTHORISATION SUCCESS"
    })
    void doUserCancel_chargeStatusDuringAuthorisation_needsCancellationWithProvider(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);

        String externalChargeId = "external-charge-id";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(status)
                .build();

        WorldpayCancelResponse worldpayResponse = new WorldpayCancelResponse();
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));
        if ("AUTHORISATION SUCCESS".equals(chargeStatus)) {
            when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
            when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);
        }

        chargeCancelService.doUserCancel(externalChargeId);

        if ("AUTHORISATION SUCCESS".equals(chargeStatus)) {
            verify(mockPaymentProvider).cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)));
        }
        verify(chargeService).transitionChargeState(externalChargeId, USER_CANCELLED);
    }

    @Test
    void doUserCancel_shouldCancel_chargeStatusAfterAuthorisation_cancelledWithProvider() throws Exception {
        String externalChargeId = "external-charge-id";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .build();

        WorldpayCancelResponse worldpayResponse = new WorldpayCancelResponse();
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        chargeCancelService.doUserCancel(externalChargeId);

        verify(chargeService).transitionChargeState(externalChargeId, USER_CANCELLED);
    }

    @Test
    void doUserCancel_shouldFail_whenChargeNotFound() {
        String externalChargeId = "external-charge-id";

        when(mockChargeDao.findByExternalId(externalChargeId)).thenReturn(Optional.empty());

        final Optional<ChargeEntity> maybeCharge = chargeCancelService.doUserCancel(externalChargeId);
        assertThat(maybeCharge.isPresent(), is(false));
    }

    @Test
    void doSystemCancel_shouldCancelWorldPayCharge_withStatus_awaitingCaptureRequest() throws Exception {
        String externalChargeId = "external-charge-id";
        Long gatewayAccountId = nextLong();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalChargeId)
                .withTransactionId("transaction-id")
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .build();

        WorldpayCancelResponse worldpayResponse = new WorldpayCancelResponse();
        GatewayResponse.GatewayResponseBuilder<WorldpayCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse cancelResponse = gatewayResponseBuilder.withResponse(worldpayResponse).build();

        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalChargeId, gatewayAccountId)).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        verify(chargeService).transitionChargeState(externalChargeId, SYSTEM_CANCELLED);
        verifyNoMoreInteractions(ignoreStubs(mockChargeDao));
    }

    @Test
    void doSystemCancel_shouldCancelEPDQCharge_withStatus_awaitingCaptureRequest() throws Exception {
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
        when(mockPaymentProviders.byName(chargeEntity.getPaymentGatewayName())).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(argThat(aCancelGatewayRequestMatching(chargeEntity)))).thenReturn(cancelResponse);

        chargeCancelService.doSystemCancel(externalChargeId, gatewayAccountId);

        verify(chargeService).transitionChargeState(externalChargeId, SYSTEM_CANCELLED);
        verifyNoMoreInteractions(ignoreStubs(mockChargeDao));
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
