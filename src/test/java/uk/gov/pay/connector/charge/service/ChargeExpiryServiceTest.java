package uk.gov.pay.connector.charge.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ChargeSweepConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.worldpay.WorldpayBaseResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.service.ChargeExpiryService.EXPIRABLE_REGULAR_STATUSES;
import static uk.gov.pay.connector.charge.service.ChargeExpiryService.GATEWAY_CANCELLABLE_STATUSES;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

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

    @Mock
    private ChargeSweepConfig mockedChargeSweepConfig;

    @Mock
    private ConnectorConfiguration mockedConfig;

    private GatewayResponse<BaseCancelResponse> gatewayResponse;
    private GatewayAccountEntity gatewayAccount;

    @Before
    public void setup() {
        when(mockedConfig.getChargeSweepConfig()).thenReturn(mockedChargeSweepConfig);
        chargeExpiryService = new ChargeExpiryService(mockChargeDao, mockChargeEventDao, mockPaymentProviders, mockedConfig);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();
        gatewayResponse = gatewayResponseBuilder.withResponse(mockWorldpayCancelResponse).build();
        gatewayAccount = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccount.setGatewayName("worldpay");
    }

    @Test
    public void shouldExpireChargesWithStatus_authorisationSuccess_byCallingProviderToCancel() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now())
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<CancelGatewayRequest> cancelCaptor = ArgumentCaptor.forClass(CancelGatewayRequest.class);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture());

        chargeExpiryService.expire(singletonList(chargeEntity));

        verify(mockPaymentProvider).cancel(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().getTransactionId(), is(chargeEntity.getGatewayTransactionId()));
        assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
    }

    @Test
    public void shouldExpireChargesWithStatus_authorisation3DSRequired_byCallingProviderToCancel() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now())
                .withStatus(ChargeStatus.AUTHORISATION_3DS_REQUIRED)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<CancelGatewayRequest> cancelCaptor = ArgumentCaptor.forClass(CancelGatewayRequest.class);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture());

        chargeExpiryService.expire(singletonList(chargeEntity));

        verify(mockPaymentProvider).cancel(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().getTransactionId(), is(chargeEntity.getGatewayTransactionId()));
        assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
    }

    @Test
    public void shouldExpireChargesWithStatus_awaitingCaptureRequest_byCallingProviderToCancel() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now())
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);
        ArgumentCaptor<CancelGatewayRequest> cancelCaptor = ArgumentCaptor.forClass(CancelGatewayRequest.class);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture());

        chargeExpiryService.expire(singletonList(chargeEntity));

        verify(mockPaymentProvider).cancel(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().getTransactionId(), is(chargeEntity.getGatewayTransactionId()));
        assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
    }

    @Test
    public void shouldExpireChargesWithoutCallingProviderToCancel() {
        EXPIRABLE_REGULAR_STATUSES.stream()
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
                    doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture());

                    chargeExpiryService.expire(singletonList(chargeEntity));

                    verify(mockPaymentProvider, never()).cancel(any());
                    assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
                });
    }

    @Test
    public void shouldUpdateStatusWhenCancellationFails() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now())
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        GatewayError mockGatewayError = mock(GatewayError.class);
        GatewayResponseBuilder<WorldpayBaseResponse> gatewayResponseBuilder = responseBuilder();
        GatewayResponse<BaseCancelResponse> gatewayErrorResponse = gatewayResponseBuilder
                .withGatewayError(mockGatewayError)
                .build();

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayErrorResponse);
        ArgumentCaptor<ChargeEntity> captor = ArgumentCaptor.forClass(ChargeEntity.class);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(captor.capture());

        chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(chargeEntity.getStatus(), is(ChargeStatus.EXPIRE_CANCEL_FAILED.getValue()));
    }

    @Test
    public void shouldSweepAndExpireCharges() {
        ChargeEntity chargeEntityAwaitingCapture = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now().minusHours(48L).plusMinutes(1L))
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        ChargeEntity chargeEntityAuthorisationSuccess = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(ZonedDateTime.now().minusHours(48L).plusMinutes(1L))
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntityAwaitingCapture.getExternalId())).thenReturn(Optional.of(chargeEntityAwaitingCapture));
        when(mockChargeDao.findByExternalId(chargeEntityAuthorisationSuccess.getExternalId())).thenReturn(Optional.of(chargeEntityAuthorisationSuccess));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        doNothing().when(mockChargeEventDao).persistChargeEventOf(any(ChargeEntity.class));
        when(mockChargeDao.findBeforeDateWithStatusIn(any(ZonedDateTime.class), eq(ChargeExpiryService.EXPIRABLE_AWAITING_CAPTURE_REQUEST_STATUS))).thenReturn(singletonList(chargeEntityAwaitingCapture));
        when(mockChargeDao.findBeforeDateWithStatusIn(any(ZonedDateTime.class), eq(ChargeExpiryService.EXPIRABLE_REGULAR_STATUSES))).thenReturn(singletonList(chargeEntityAuthorisationSuccess));

        chargeExpiryService.sweepAndExpireCharges();

        assertThat(chargeEntityAwaitingCapture.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
        assertThat(chargeEntityAuthorisationSuccess.getStatus(), is(ChargeStatus.EXPIRED.getValue()));
    }
}
