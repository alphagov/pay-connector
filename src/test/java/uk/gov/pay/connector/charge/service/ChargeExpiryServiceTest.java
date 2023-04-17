package uk.gov.pay.connector.charge.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.google.common.collect.ImmutableList;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ChargeSweepConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCancelResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;
import uk.gov.pay.connector.token.dao.TokenDao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@RunWith(JUnitParamsRunner.class)
public class ChargeExpiryServiceTest {

    private ChargeExpiryService chargeExpiryService;

    private final Clock fixedClock = Clock.fixed(Instant.ofEpochSecond(1654732800), ZoneId.of("UTC")); // Thu 9 June 2022 00:00:00

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeService mockChargeService;

    @Mock
    private TokenDao mockTokenDao;

    @Mock
    private IdempotencyDao mockIdempotencyDao;
    
    @Mock
    private PaymentProviders mockPaymentProviders;

    @Mock
    private PaymentProvider mockPaymentProvider;

    @Mock
    private QueryService mockQueryService;

    @Mock
    private WorldpayCancelResponse mockWorldpayCancelResponse;

    @Mock
    private ChargeSweepConfig mockedChargeSweepConfig;

    @Mock
    private ConnectorConfiguration mockedConfig;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    private static final List<ChargeStatus> EXPIRABLE_REGULAR_STATUSES = ImmutableList.of(
            CREATED,
            ENTERING_CARD_DETAILS,
            AUTHORISATION_READY,
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_3DS_READY,
            AUTHORISATION_SUCCESS
    );

    private static final List<ChargeStatus> EXPIRABLE_AWAITING_CAPTURE_REQUEST_STATUS = ImmutableList.of(
            AWAITING_CAPTURE_REQUEST
    );

    private static final Duration TOKEN_EXPIRY_WINDOW = Duration.ofSeconds(7 * 24 * 60 * 60);
    private static final Duration IDEMPOTENCY_EXPIRY_WINDOW = Duration.ofSeconds(24 * 60 * 60);
    private static final Duration CHARGE_EXPIRY_WINDOW = Duration.ofSeconds((long) (1.5 * 60 * 60));
    private static final Duration AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW = Duration.ofSeconds(5 * 24 * 60 * 60);

    private GatewayResponse<BaseCancelResponse> gatewayResponse;
    private GatewayAccountEntity gatewayAccount;

    @Before
    public void setup() {
        when(mockedConfig.getChargeSweepConfig()).thenReturn(mockedChargeSweepConfig);
        chargeExpiryService = new ChargeExpiryService(mockChargeDao, mockChargeService, mockTokenDao, mockIdempotencyDao, mockPaymentProviders, mockQueryService, mockedConfig, fixedClock);
        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();
        gatewayResponse = gatewayResponseBuilder.withResponse(mockWorldpayCancelResponse).build();
        gatewayAccount = ChargeEntityFixture.defaultGatewayAccountEntity();
    }

    private ChargeEntity mockExpiredChargeEntity() {

        ChargeEntity expiredCharge = mock(ChargeEntity.class);
        when(expiredCharge.getStatus()).thenReturn(EXPIRED.toString());
        return expiredCharge;
    }

    @Test
    @Parameters({
            "CREATED",
            "ENTERING CARD DETAILS"
    })
    public void shouldExpireChargesWithoutGateway_whenStateIsPreAuthorisation(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now())
                .withStatus(status)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        Map<String, Integer> sweepResult = chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(sweepResult.get("expiry-success"), is(1));
        assertThat(sweepResult.get("expiry-failed"), is(0));

        verify(mockPaymentProvider, never()).cancel(any());
        verify(mockChargeService).transitionChargeState(chargeEntity.getExternalId(), EXPIRED);
    }

    @Test
    @Parameters({
            "AUTHORISATION 3DS REQUIRED",
            "AUTHORISATION 3DS READY"
    })
    public void shouldExpireChargesWithoutGateway_whenStateIsDuringAuthorisationAndCannotCheckStatusWithGateway(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now())
                .withStatus(status)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockQueryService.canQueryChargeGatewayStatus(chargeEntity.getPaymentGatewayName())).thenReturn(false);

        Map<String, Integer> sweepResult = chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(sweepResult.get("expiry-success"), is(1));
        assertThat(sweepResult.get("expiry-failed"), is(0));

        verify(mockPaymentProvider, never()).cancel(any());
        verify(mockChargeService).transitionChargeState(chargeEntity.getExternalId(), EXPIRED);
    }

    @Test
    @Parameters({
            "AUTHORISATION SUCCESS",
            "AWAITING CAPTURE REQUEST"
    })
    public void shouldExpireWithGateway_whenStateIsPostAuthorisationAndCannotCheckStatusWithGateway(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now())
                .withStatus(status)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockQueryService.canQueryChargeGatewayStatus(chargeEntity.getPaymentGatewayName())).thenReturn(false);

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        ArgumentCaptor<CancelGatewayRequest> cancelCaptor = ArgumentCaptor.forClass(CancelGatewayRequest.class);

        ChargeEntity expiredCharge = mockExpiredChargeEntity();
        when(mockChargeService.transitionChargeState(chargeEntity.getExternalId(), EXPIRED)).thenReturn(expiredCharge);

        Map<String, Integer> sweepResult = chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(sweepResult.get("expiry-success"), is(1));
        assertThat(sweepResult.get("expiry-failed"), is(0));

        verify(mockPaymentProvider).cancel(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().getTransactionId(), is(chargeEntity.getGatewayTransactionId()));
    }

    @Test
    @Parameters({
            "AUTHORISATION 3DS REQUIRED",
            "AUTHORISATION 3DS READY",
            "AUTHORISATION SUCCESS",
            "AWAITING CAPTURE REQUEST"
    })
    public void shouldExpireWithGateway_whenCanCheckWithGatewayAndGatewayStatusIsNotTerminal(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now())
                .withStatus(status)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockQueryService.canQueryChargeGatewayStatus(chargeEntity.getPaymentGatewayName())).thenReturn(true);
        when(mockQueryService.getMappedGatewayStatus(chargeEntity)).thenReturn(Optional.of(AUTHORISATION_SUCCESS));

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        ArgumentCaptor<CancelGatewayRequest> cancelCaptor = ArgumentCaptor.forClass(CancelGatewayRequest.class);

        ChargeEntity expiredCharge = mockExpiredChargeEntity();
        when(mockChargeService.transitionChargeState(chargeEntity.getExternalId(), EXPIRED)).thenReturn(expiredCharge);

        Map<String, Integer> sweepResult = chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(sweepResult.get("expiry-success"), is(1));
        assertThat(sweepResult.get("expiry-failed"), is(0));

        verify(mockPaymentProvider).cancel(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().getTransactionId(), is(chargeEntity.getGatewayTransactionId()));
    }

    @Test
    @Parameters({
            "AUTHORISATION 3DS REQUIRED",
            "AUTHORISATION 3DS READY",
            "AUTHORISATION SUCCESS",
            "AWAITING CAPTURE REQUEST"
    })
    public void shouldUpdateStatusToMatchGatewayStatus_whenCanCheckWithGatewayAndGatewayStatusIsTerminal(String chargeStatus) throws Exception {
        var status = ChargeStatus.fromString(chargeStatus);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now())
                .withStatus(status)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockQueryService.canQueryChargeGatewayStatus(chargeEntity.getPaymentGatewayName())).thenReturn(true);
        when(mockQueryService.getMappedGatewayStatus(chargeEntity)).thenReturn(Optional.of(CAPTURED));

        ChargeEntity updatedCharge = mock(ChargeEntity.class);
        when(mockChargeService.transitionChargeState(chargeEntity.getExternalId(), CAPTURED)).thenThrow(InvalidStateTransitionException.class);
        when(mockChargeService.forceTransitionChargeState(chargeEntity.getExternalId(), CAPTURED)).thenReturn(updatedCharge);

        Map<String, Integer> sweepResult = chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(sweepResult.get("expiry-success"), is(1));
        assertThat(sweepResult.get("expiry-failed"), is(0));

        verify(mockPaymentProvider, never()).cancel(any());
        verify(mockChargeService).forceTransitionChargeState(chargeEntity.getExternalId(), CAPTURED);
    }

    @Test
    public void shouldUpdateStatusToMatchGatewayStatus_whenNormalStateTransitionAllowed() throws Exception {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now())
                .withStatus(AUTHORISATION_3DS_READY)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockQueryService.canQueryChargeGatewayStatus(chargeEntity.getPaymentGatewayName())).thenReturn(true);
        when(mockQueryService.getMappedGatewayStatus(chargeEntity)).thenReturn(Optional.of(AUTHORISATION_REJECTED));

        ChargeEntity updatedCharge = mock(ChargeEntity.class);
        when(mockChargeService.transitionChargeState(chargeEntity.getExternalId(), AUTHORISATION_REJECTED)).thenReturn(updatedCharge);

        Map<String, Integer> sweepResult = chargeExpiryService.expire(singletonList(chargeEntity));

        assertThat(sweepResult.get("expiry-success"), is(1));
        assertThat(sweepResult.get("expiry-failed"), is(0));

        verify(mockPaymentProvider, never()).cancel(any());
        verify(mockChargeService).transitionChargeState(chargeEntity.getExternalId(), AUTHORISATION_REJECTED);
    }

    @Test
    public void shouldUpdateStatusWhenCancellationFails() throws Exception {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now())
                .withStatus(ChargeStatus.AUTHORISATION_SUCCESS)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockChargeDao.findByExternalId(chargeEntity.getExternalId())).thenReturn(Optional.of(chargeEntity));
        when(mockPaymentProvider.cancel(any())).thenThrow(new GatewayException.GenericGatewayException("something went wrong"));

        ChargeEntity expireFailedCharge = mock(ChargeEntity.class);
        when(expireFailedCharge.getStatus()).thenReturn(EXPIRE_CANCEL_FAILED.toString());
        when(mockChargeService.transitionChargeState(eq(chargeEntity.getExternalId()), any())).thenReturn(expireFailedCharge);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);

        chargeExpiryService.expire(singletonList(chargeEntity));

        verify(mockChargeService).transitionChargeState(chargeEntity.getExternalId(), EXPIRE_CANCEL_FAILED);
    }

    @Test
    public void shouldSweepAndExpireCharges() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(ChargeExpiryService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        ChargeEntity chargeEntityAwaitingCapture = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(48)).plus(Duration.ofMinutes(1)))
                .withStatus(AWAITING_CAPTURE_REQUEST)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        ChargeEntity chargeEntityAuthorisationSuccess = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(48)).plus(Duration.ofMinutes(1)))
                .withStatus(AUTHORISATION_SUCCESS)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);

        when(mockChargeDao.findByExternalId(chargeEntityAwaitingCapture.getExternalId())).thenReturn(Optional.of(chargeEntityAwaitingCapture));
        when(mockChargeDao.findByExternalId(chargeEntityAuthorisationSuccess.getExternalId())).thenReturn(Optional.of(chargeEntityAuthorisationSuccess));
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);
        when(mockChargeDao.findBeforeDateWithStatusIn(any(Instant.class),
                eq(EXPIRABLE_AWAITING_CAPTURE_REQUEST_STATUS))).thenReturn(singletonList(chargeEntityAwaitingCapture));
        when(mockChargeDao.findChargesByCreatedUpdatedDatesAndWithStatusIn(any(Instant.class), any(Instant.class),
                eq(EXPIRABLE_REGULAR_STATUSES))).thenReturn(singletonList(chargeEntityAuthorisationSuccess));
        when(mockedChargeSweepConfig.getTokenExpiryThresholdInSeconds()).thenReturn(TOKEN_EXPIRY_WINDOW);
        when(mockedChargeSweepConfig.getDefaultChargeExpiryThreshold()).thenReturn(CHARGE_EXPIRY_WINDOW);
        when(mockedChargeSweepConfig.getIdempotencyKeyExpiryThresholdInSeconds()).thenReturn(IDEMPOTENCY_EXPIRY_WINDOW);
        when(mockedChargeSweepConfig.getSkipExpiringChargesLastUpdatedInSeconds()).thenReturn(Duration.ofSeconds(120L));
        when(mockedChargeSweepConfig.getAwaitingCaptureExpiryThreshold()).thenReturn(AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW);
        when(mockTokenDao.deleteTokensOlderThanSpecifiedDate(fixedClock.instant().minus(TOKEN_EXPIRY_WINDOW).atZone(ZoneId.of("UTC")))).thenReturn(1);
        when(mockIdempotencyDao.deleteIdempotencyKeysOlderThanSpecifiedDateTime(fixedClock.instant().minus(IDEMPOTENCY_EXPIRY_WINDOW))).thenReturn(1);

        ChargeEntity expiredCharge = mockExpiredChargeEntity();
        when(mockChargeService.transitionChargeState(any(String.class), any())).thenReturn(expiredCharge);

        chargeExpiryService.sweepAndExpireChargesAndTokensAndIdempotencyKeys();
        verify(mockAppender, times(5)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.stream().map(LoggingEvent::getFormattedMessage).collect(Collectors.toList()),
                hasItems(
                        "Tokens deleted - number_of_tokens=1, since_date=2022-06-02T00:00:00Z",
                        "Idempotency keys deleted - number_of_idempotency_keys=1, since_date=2022-06-08T00:00:00Z",
                        "Charges found for expiry - number_of_charges=2, since_date=2022-06-08T22:30:00Z, updated_before=2022-06-08T23:58:00Z, awaiting_capture_date=2022-06-04T00:00:00Z"
                ));
        verify(mockChargeService).transitionChargeState(chargeEntityAwaitingCapture.getExternalId(), EXPIRED);
        verify(mockChargeService).transitionChargeState(chargeEntityAuthorisationSuccess.getExternalId(), EXPIRED);
        verify(mockIdempotencyDao).deleteIdempotencyKeysOlderThanSpecifiedDateTime(Instant.parse("2022-06-08T00:00:00Z"));
    }

    @Test
    public void shouldCancelChargeWithGatewayWhenChargeInPreAuthorisedStateAndExistsWithGateway() throws Exception {
        ChargeEntity preAuthorisationCharge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(48)).plus(Duration.ofMinutes(1)))
                .withStatus(ChargeStatus.AUTHORISATION_3DS_READY)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        ChargeEntity expiredCharge = mock(ChargeEntity.class);

        when(mockChargeDao.findChargesByCreatedUpdatedDatesAndWithStatusIn(any(Instant.class), any(Instant.class),
                eq(EXPIRABLE_REGULAR_STATUSES))).thenReturn(singletonList(preAuthorisationCharge));

        when(mockChargeService.transitionChargeState(any(String.class), any())).thenReturn(expiredCharge);

        Map<String, Integer> sweepResult = chargeExpiryService.sweepAndExpireChargesAndTokensAndIdempotencyKeys();

        verify(mockChargeService).transitionChargeState(preAuthorisationCharge.getExternalId(), EXPIRED);
        assertThat(sweepResult.get("expiry-success"), is(1));
        assertThat(sweepResult.get("expiry-failed"), is(0));
    }

    @Test
    public void forceCancelShouldReturnSuccess_whenCancelStateIsCancelled() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(48)).plus(Duration.ofMinutes(1)))
                .withStatus(ChargeStatus.AUTHORISATION_3DS_READY)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.CANCELLED);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);

        Boolean cancelSuccess = chargeExpiryService.forceCancelWithGateway(charge);

        assertThat(cancelSuccess, is(true));
    }

    @Test
    public void forceCancelShouldReturnSuccess_whenCancelStateIsSubmitted() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(48)).plus(Duration.ofMinutes(1)))
                .withStatus(ChargeStatus.AUTHORISATION_3DS_READY)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.SUBMITTED);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);

        Boolean cancelSuccess = chargeExpiryService.forceCancelWithGateway(charge);

        assertThat(cancelSuccess, is(true));
    }

    @Test
    public void forceCancelShouldReturnFailure_whenCancelStateIsError() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(48)).plus(Duration.ofMinutes(1)))
                .withStatus(ChargeStatus.AUTHORISATION_3DS_READY)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();
        when(mockPaymentProvider.cancel(any())).thenReturn(gatewayResponse);
        when(mockWorldpayCancelResponse.cancelStatus()).thenReturn(CancelStatus.ERROR);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);

        Boolean cancelSuccess = chargeExpiryService.forceCancelWithGateway(charge);

        assertThat(cancelSuccess, is(false));
    }

    @Test
    public void forceCancelShouldReturnFailure_whenGatewayResponseHasError() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(200L)
                .withCreatedDate(Instant.now().minus(Duration.ofHours(48)).plus(Duration.ofMinutes(1)))
                .withStatus(ChargeStatus.AUTHORISATION_3DS_READY)
                .withPaymentProvider("worldpay")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        GatewayResponse mockGatewayResponse = mock(GatewayResponse.class);
        when(mockGatewayResponse.getBaseResponse()).thenReturn(Optional.empty());
        when(mockGatewayResponse.getGatewayError()).thenReturn(Optional.of(GatewayError.genericGatewayError("Error")));
        when(mockPaymentProvider.cancel(any())).thenReturn(mockGatewayResponse);
        when(mockPaymentProviders.byName(PaymentGatewayName.WORLDPAY)).thenReturn(mockPaymentProvider);

        Boolean cancelSuccess = chargeExpiryService.forceCancelWithGateway(charge);

        assertThat(cancelSuccess, is(false));
    }
}
