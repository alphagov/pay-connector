package uk.gov.pay.connector.queue.tasks.handlers.adyen.fee;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.Level;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeSubType;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.refund.RefundFeeIncurredEvent;
import uk.gov.pay.connector.fee.dao.FeeDao;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTransferDataFixture;
import uk.gov.pay.connector.gateway.adyen.webhook.json.transfer.AdyenPlatformPaymentCategory;
import uk.gov.pay.connector.gateway.adyen.webhook.json.transfer.AdyenTransferData;
import uk.gov.pay.connector.gateway.adyen.webhook.json.transfer.AdyenTransferNotification;
import uk.gov.pay.connector.gateway.adyen.webhook.json.transfer.Amount;
import uk.gov.pay.connector.refund.exception.RefundNotFoundRuntimeException;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.FIXED;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.INTERCHANGE;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.SCHEME_FEE;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.VARIABLE;
import static uk.gov.pay.connector.charge.model.domain.FeeType.FRAUD_PROTECTION;
import static uk.gov.pay.connector.charge.model.domain.FeeType.GATEWAY;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;

@ExtendWith(MockitoExtension.class)
class AdyenTransferNotificationHandlerForFeesTest {

    @Mock
    private AdyenGatewayConfig mockAdyenGatewayConfig;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private RefundService mockRefundService;
    @Mock
    private EventService mockEventService;
    @Mock
    private FeeDao mockFeeDao;
    @Mock
    private InstantSource instantSource;

    private ChargeEntity chargeEntity;
    private RefundEntity refundEntity;
    private AdyenTransferNotificationHandlerForFees handler;
    private final ArgumentCaptor<FeeEntity> argumentCaptor = ArgumentCaptor.forClass(FeeEntity.class);

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenTransferNotificationHandlerForFees.class);

    @BeforeEach
    void setUp() {
        chargeEntity = aValidChargeEntity()
                .withId(secureRandomLong())
                .build();
        refundEntity = aValidRefundEntity()
                .withId(secureRandomLong())
                .build();
        handler = new AdyenTransferNotificationHandlerForFees(mockAdyenGatewayConfig,
                mockChargeService,
                mockRefundService,
                mockEventService,
                mockFeeDao,
                instantSource);
    }

    @Test
    void shouldProcessCaptureTransferForFixedCommission() {
        var chargeExternalId = "charge-external-id-123";
        AdyenTransferNotification adyenTransferNotification = buildNotification(chargeExternalId, null,
                "capture", "captured", "Commission", "Fixed", 100L, "test");

        when(mockChargeService.findChargeByExternalId(chargeExternalId)).thenReturn(chargeEntity);
        when(mockAdyenGatewayConfig.getPaymentOrRefundGatewayFeeInPence()).thenReturn(10);
        when(mockAdyenGatewayConfig.getFraudAvoidanceFeeInPence()).thenReturn(20);
        when(instantSource.instant()).thenReturn(NOW);

        handler.process(adyenTransferNotification);

        verify(mockChargeService).findChargeByExternalId(chargeExternalId);
        verify(mockFeeDao, times(3))
                .insertChargeFeeIfAbsent(argumentCaptor.capture(), eq(chargeEntity.getId()));

        List<FeeEntity> feeEntities = argumentCaptor.getAllValues();

        assertThat(feeEntities.size(), is(3));
        FeeEntity feeEntity = feeEntities.getFirst();
        assertThat(feeEntity.getFeeType(), is(GATEWAY));
        assertThat(feeEntity.getFeeSubType(), is(nullValue()));
        assertThat(feeEntity.getAmountDue(), is(10L));

        feeEntity = feeEntities.get(1);
        assertThat(feeEntity.getFeeType(), is(FRAUD_PROTECTION));
        assertThat(feeEntity.getFeeSubType(), is(nullValue()));
        assertThat(feeEntity.getAmountDue(), is(20L));

        feeEntity = feeEntities.get(2);
        assertThat(feeEntity.getFeeType(), is(TRANSACTION));
        assertThat(feeEntity.getFeeSubType(), is(FIXED));
        assertThat(feeEntity.getAmountDue(), is(70L));
    }

    @ParameterizedTest
    @MethodSource("expectedFeeSubTypesForFeeTransferType")
    void shouldProcessCaptureTransferForAllNonFixedFeeTypes(String platformPaymentType,
                                                            String description,
                                                            FeeSubType expectedFeeSubType) {
        var chargeExternalId = "charge-external-id-123";
        AdyenTransferNotification adyenTransferNotification = buildNotification(chargeExternalId, null,
                "capture", "captured", platformPaymentType, description, 100L, "test");

        when(mockChargeService.findChargeByExternalId(chargeExternalId)).thenReturn(chargeEntity);
        when(mockAdyenGatewayConfig.getPaymentOrRefundGatewayFeeInPence()).thenReturn(10);
        when(mockAdyenGatewayConfig.getFraudAvoidanceFeeInPence()).thenReturn(20);
        when(instantSource.instant()).thenReturn(NOW);

        handler.process(adyenTransferNotification);

        verify(mockChargeService).findChargeByExternalId(chargeExternalId);
        verify(mockFeeDao, times(1))
                .insertChargeFeeIfAbsent(argumentCaptor.capture(), eq(chargeEntity.getId()));

        FeeEntity feeEntity = argumentCaptor.getValue();
        assertThat(feeEntity.getFeeType(), is(TRANSACTION));
        assertThat(feeEntity.getFeeSubType(), is(expectedFeeSubType));
        assertThat(feeEntity.getAmountDue(), is(100L));
    }

    private static Stream<Arguments> expectedFeeSubTypesForFeeTransferType() {
        return Stream.of(
                Arguments.of("Commission", "Variable", VARIABLE),
                Arguments.of("Interchange", "Some desc", INTERCHANGE),
                Arguments.of("SchemeFee", "Some desc", SCHEME_FEE)
        );
    }

    @Test
    void shouldSkipCaptureTransferForUnsupportedFeeType() {
        AdyenTransferNotification adyenTransferNotification = buildNotification("some-id", null,
                "capture", "captured", "Unsupported", "Fixed", 100L, "test");

        handler.process(adyenTransferNotification);

        verifyNoInteractions(mockChargeService, mockFeeDao, instantSource);
    }

    @Test
    void shouldProcessRefundTransferEvent() {
        var refundExternalId = "refund-external-id-123";
        AdyenTransferNotification adyenTransferNotification = buildNotification(refundExternalId, refundExternalId,
                "refund", "refunded", "PaymentFee", "Refund Fee", 10L, "test");

        when(instantSource.instant()).thenReturn(NOW);
        var feeAmount = adyenTransferNotification.data().amount().value();
        var refundFee = Fee.of(GATEWAY, feeAmount, null);
        refundEntity.addFee(new FeeEntity(refundEntity, instantSource.instant(), refundFee));
        when(mockRefundService.findRefundByExternalId(refundExternalId)).thenReturn(Optional.of(refundEntity));
        when(mockFeeDao.insertRefundFeeIfAbsent(any(FeeEntity.class), eq(refundEntity.getId()))).thenReturn(true);
        when(mockAdyenGatewayConfig.getPaymentOrRefundGatewayFeeInPence()).thenReturn(10);
        
        handler.process(adyenTransferNotification);

        verify(mockRefundService, times(2)).findRefundByExternalId(refundExternalId);

        verify(mockFeeDao, times(1))
                .insertRefundFeeIfAbsent(argumentCaptor.capture(), eq(refundEntity.getId()));
        verify(mockEventService).emitAndRecordEvent(any(RefundFeeIncurredEvent.class));

        List<FeeEntity> feeEntities = argumentCaptor.getAllValues();

        assertThat(feeEntities.size(), is(1));
        FeeEntity feeEntity = feeEntities.getFirst();
        assertThat(feeEntity.getFeeType(), is(GATEWAY));
        assertThat(feeEntity.getFeeSubType(), is(nullValue()));
        assertThat(feeEntity.getAmountDue(), is(10L));

        logs.assertContains("Processed transfer notification for refund fees");
    }

    @Test
    void shouldNotEmitFeeEventForRefundWhenFeeAlreadyExists() {
        var refundExternalId = "refund-external-id-123";
        AdyenTransferNotification adyenTransferNotification = buildNotification(refundExternalId, refundExternalId,
                "refund", "refunded", "PaymentFee", "Refund Fee", 10L, "test");

        when(mockRefundService.findRefundByExternalId(refundExternalId)).thenReturn(Optional.of(refundEntity));
        when(mockFeeDao.insertRefundFeeIfAbsent(any(FeeEntity.class), eq(refundEntity.getId()))).thenReturn(false);

        handler.process(adyenTransferNotification);

        verify(mockRefundService).findRefundByExternalId(refundExternalId);

        verify(mockFeeDao, times(1))
                .insertRefundFeeIfAbsent(argumentCaptor.capture(), eq(refundEntity.getId()));
        verifyNoInteractions(mockEventService);

        logs.assertContains("Refund fee already exists");
    }

    @ParameterizedTest
    @CsvSource({
            "live, ERROR",
            "test, WARN"
    })
    void shouldProcessRefundTransferEventWhenFeeDoesNotMatchConfig(String environment, String logLevel) {
        var refundExternalId = "refund-external-id-123";
        AdyenTransferNotification adyenTransferNotification = buildNotification(refundExternalId, refundExternalId,
                "refund", "refunded", "PaymentFee", "Refund Fee", 10L,
                environment);

        when(instantSource.instant()).thenReturn(NOW);
        var feeAmount = adyenTransferNotification.data().amount().value();
        var refundFee = Fee.of(GATEWAY, feeAmount, null);
        refundEntity.addFee(new FeeEntity(refundEntity, instantSource.instant(), refundFee));
        when(mockRefundService.findRefundByExternalId(refundExternalId)).thenReturn(Optional.of(refundEntity));
        when(mockAdyenGatewayConfig.getPaymentOrRefundGatewayFeeInPence()).thenReturn(50);
        when(mockFeeDao.insertRefundFeeIfAbsent(any(FeeEntity.class), eq(refundEntity.getId()))).thenReturn(true);

        handler.process(adyenTransferNotification);

        verify(mockRefundService, times(2)).findRefundByExternalId(refundExternalId);

        verify(mockFeeDao, times(1))
                .insertRefundFeeIfAbsent(argumentCaptor.capture(), eq(refundEntity.getId()));
        verify(mockEventService).emitAndRecordEvent(any(RefundFeeIncurredEvent.class));

        List<FeeEntity> feeEntities = argumentCaptor.getAllValues();

        assertThat(feeEntities.size(), is(1));
        FeeEntity feeEntity = feeEntities.getFirst();
        assertThat(feeEntity.getFeeType(), is(GATEWAY));
        assertThat(feeEntity.getFeeSubType(), is(nullValue()));
        assertThat(feeEntity.getAmountDue(), is(10L));

        logs.assertContains("Processed transfer notification for refund fees");
        var actualLogLevel = logs.assertContains("Refund fee does not match Adyen gateway config fee value")
                .getLevel();
        assertEquals(Level.valueOf(logLevel), actualLogLevel);
    }

    @Test
    void shouldSkipRefundTransferWhenPlatformPaymentTypeIsNotPaymentFee() {
        var refundExternalId = "refund-external-id-123";
        AdyenTransferNotification adyenTransferNotification = buildNotification(refundExternalId, refundExternalId,
                "refund", "refunded", "Not PaymentFee", "Refund Fee", 10L, "test");

        handler.process(adyenTransferNotification);

        verifyNoInteractions(mockRefundService, mockFeeDao, mockEventService, instantSource);
        logs.assertContains("Skipping transfer notification for refund fees as it is not PaymentFee payment type");
    }

    @Test
    void shouldSkipRefundTransferWhenRefundEntityNotFound() {
        var refundExternalId = "refund-external-id-123";
        AdyenTransferNotification adyenTransferNotification = buildNotification(refundExternalId, refundExternalId,
                "refund", "refunded", "PaymentFee", "Refund Fee", 10L, "test");

        when(mockRefundService.findRefundByExternalId(refundExternalId)).thenReturn(Optional.empty());

        assertThrows(RefundNotFoundRuntimeException.class, () -> handler.process(adyenTransferNotification));

        verifyNoInteractions(mockFeeDao, mockEventService, instantSource);
    }

    @Test
    void shouldHandleErrorWhenEmittingEvent() {
        var refundExternalId = "refund-external-id-123";

        AdyenTransferNotification adyenTransferNotification = buildNotification(refundExternalId, refundExternalId,
                "refund", "refunded", "PaymentFee", "Refund Fee", 10L, "test");

        when(instantSource.instant()).thenReturn(NOW);
        var feeAmount = adyenTransferNotification.data().amount().value();
        var refundFee = Fee.of(GATEWAY, feeAmount, null);
        refundEntity.addFee(new FeeEntity(refundEntity, instantSource.instant(), refundFee));
        when(mockRefundService.findRefundByExternalId(refundExternalId)).thenReturn(Optional.of(refundEntity));
        
        when(mockFeeDao.insertRefundFeeIfAbsent(any(FeeEntity.class), eq(refundEntity.getId()))).thenReturn(true);
        
        doThrow(new RuntimeException("Event emission failed"))
                .when(mockEventService)
                .emitAndRecordEvent(any(RefundFeeIncurredEvent.class));
        
        assertThrows(RuntimeException.class, () -> handler.process(adyenTransferNotification));

        logs.assertContains("Error emitting FEE_INCURRED event for refund");
    }

    @Test
    void shouldIgnoreUnknownTransactionType() {
        AdyenTransferNotification adyenTransferNotification = buildNotification("some-id", null,
                "unknown", "captured", "Unsupported", "Fixed", 100L, "test");

        handler.process(adyenTransferNotification);

        verifyNoInteractions(mockChargeService, mockFeeDao, instantSource);
    }

    @Test
    void shouldRejectNotificationForMissingAmount() {
        AdyenTransferNotification adyenTransferNotification = buildNotification(
                "some-id", null, "capture", "captured",
                "Unsupported", "Fixed", null, "test");

        assertThrows(IllegalArgumentException.class,
                () -> handler.process(adyenTransferNotification)
        );

        verifyNoInteractions(mockChargeService, mockFeeDao, instantSource);
    }

    @Test
    void shouldRejectNotificationWithMissingStatus() {
        AdyenTransferNotification adyenTransferNotification = buildNotification(
                "some-id", null, "capture", null,
                "Unsupported", "Fixed", 10L, "test");

        assertThrows(IllegalArgumentException.class,
                () -> handler.process(adyenTransferNotification)
        );

        verifyNoInteractions(mockChargeService, mockFeeDao, instantSource);
    }

    @Test
    void shouldRejectNotificationWithMissingType() {
        AdyenTransferNotification adyenTransferNotification = buildNotification(
                "some-id", null, null, "captured",
                "Unsupported", "Fixed", 10L, "test");

        assertThrows(IllegalArgumentException.class,
                () -> handler.process(adyenTransferNotification)
        );

        verifyNoInteractions(mockChargeService, mockFeeDao, instantSource);
    }

    private static AdyenTransferNotification buildNotification(String chargeExternalId, String refundExternalId,
                                                               String type, String status, String platformPaymentType,
                                                               String description, Long amount, String environment) {

        AdyenPlatformPaymentCategory adyenPlatformPaymentCategory = null;
        if (platformPaymentType != null) {
            adyenPlatformPaymentCategory = new AdyenPlatformPaymentCategory(refundExternalId,
                    null, chargeExternalId, platformPaymentType, null, null);
        }

        Amount adyenAmount = (amount == null) ? null : new Amount("GBP", amount);

        AdyenTransferData transferData = AdyenTransferDataFixture.anAdyenTransferDataFixture()
                .withCategoryData(adyenPlatformPaymentCategory)
                .withStatus(status)
                .withDescription(description)
                .withAmount(adyenAmount)
                .withType(type)
                .build();

        return new AdyenTransferNotification(null, environment, transferData, null);
    }
}
