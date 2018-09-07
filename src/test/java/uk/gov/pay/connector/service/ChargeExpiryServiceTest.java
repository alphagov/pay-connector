package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.BaseCancelResponse.CancelStatus;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.worldpay.WorldpayBaseResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCancelResponse;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.ChargeExpiryService.EXPIRABLE_STATUSES;
import static uk.gov.pay.connector.service.ChargeExpiryService.GATEWAY_CANCELLABLE_STATUSES;

@RunWith(MockitoJUnitRunner.class)
public class ChargeExpiryServiceTest {

    private ChargeExpiryService chargeExpiryService;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeEventDao mockChargeEventDao;

    @Mock
    private PaymentProviders mockPaymentProviders;

    @Mock
    private PaymentProvider mockPaymentProvider;

    @Mock
    private WorldpayCancelResponse mockWorldpayCancelResponse;

    @Before
    public void setup() {
        chargeExpiryService = new ChargeExpiryService(mockChargeDao, mockChargeEventDao, mockPaymentProviders, TransactionFlow::new);
    }

    @Test
    public void shouldExpireChargesWithStatus_authorisationSuccess_byCallingProviderToCancel() {
        GatewayAccountEntity gatewayAccount = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccount.setGatewayName("worldpay");
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now())
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<BaseCancelResponse> gatewayResponse = gatewayResponseBuilder.withResponse(mockWorldpayCancelResponse).build();

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<CancelGatewayRequest> cancelCaptor = ArgumentCaptor.forClass(CancelGatewayRequest.class);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture(), any());

        chargeExpiryService.expire(singletonList(chargeEntity));

        verify(mockPaymentProvider).cancel(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().getTransactionId(), is(chargeEntity.getGatewayTransactionId()));
        assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
    }

    @Test
    public void shouldExpireChargesWithStatus_awaitingCaptureRequest_byCallingProviderToCancel() {
        GatewayAccountEntity gatewayAccount = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccount.setGatewayName("worldpay");
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now())
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<BaseCancelResponse> gatewayResponse = gatewayResponseBuilder.withResponse(mockWorldpayCancelResponse).build();

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<CancelGatewayRequest> cancelCaptor = ArgumentCaptor.forClass(CancelGatewayRequest.class);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture(), any());

        chargeExpiryService.expire(singletonList(chargeEntity));

        verify(mockPaymentProvider).cancel(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().getTransactionId(), is(chargeEntity.getGatewayTransactionId()));
        assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
    }

    @Test
    public void shouldExpireChargesWithoutCallingProviderToCancel() {
        GatewayAccountEntity gatewayAccount = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccount.setGatewayName("worldpay");

        GatewayResponseBuilder<WorldpayBaseResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<WorldpayBaseResponse> gatewayResponse = gatewayResponseBuilder.withResponse(
                mockWorldpayCancelResponse).build();
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);

        EXPIRABLE_STATUSES.stream()
                .filter(status -> !GATEWAY_CANCELLABLE_STATUSES.contains(status))
                .forEach(status -> {
                    ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                            .withAmount(200L)
                            .withCreatedDate(ZonedDateTime.now())
                            .withStatus(status)
                            .withGatewayAccountEntity(gatewayAccount)
                            .build();
                    ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);

                    when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
                    doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture(), any());

                    chargeExpiryService.expire(singletonList(chargeEntity));

                    verify(mockPaymentProvider, never()).cancel(any());
                    assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
                });
    }

    @Test
    public void shouldUpdateStatusWhenCancellationFails() {
        GatewayAccountEntity gatewayAccount = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccount.setGatewayName("worldpay");
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now())
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        GatewayError mockGatewayError = mock(GatewayError.class);
        GatewayResponseBuilder<WorldpayBaseResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<WorldpayBaseResponse> gatewayResponse = gatewayResponseBuilder
                .withGatewayError(mockGatewayError)
                .build();

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture(), any());

        chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRE_CANCEL_FAILED.getValue()));
    }


}
