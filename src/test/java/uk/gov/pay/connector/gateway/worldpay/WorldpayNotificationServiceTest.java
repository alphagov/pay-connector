package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.IpDomainMatcher;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;

@ExtendWith(MockitoExtension.class)
class WorldpayNotificationServiceTest {
    private WorldpayNotificationService notificationService;

    @Mock
    private ChargeService mockChargeService;
    @Mock
    private GatewayAccountService mockGatewayAccountService;
    @Mock
    private WorldpayNotificationConfiguration mockWorldpayConfiguration;
    @Mock
    private IpDomainMatcher mockIpDomainMatcher;
    @Mock
    private ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock
    private RefundNotificationProcessor mockRefundNotificationProcessor;
    private Charge charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().build());
    private GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();

    private final String ipAddress = "1.1.1.1";
    private final String referenceId = "refund-reference";
    private final String refundAuthorisationReference = "refund-authorisation-reference";
    private final String transactionId = "transaction-reference";

    @BeforeEach
    void setup() {
        notificationService = new WorldpayNotificationService(
                mockChargeService,
                mockWorldpayConfiguration,
                mockIpDomainMatcher,
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor,
                mockGatewayAccountService);
    }

    @Test
    void givenAChargeCapturedNotification_chargeNotificationProcessorInvokedWithNotificationAndCharge() {
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountServiceToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "",
                "CAPTURED",
                "10",
                "03",
                "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertTrue(result);
        WorldpayNotification expectedNotification = new WorldpayNotification(
                "MERCHANTCODE",
                "CAPTURED",
                10,
                3,
                2017,
                transactionId,
                referenceId,
                ""
        );
        verify(mockChargeNotificationProcessor).invoke(expectedNotification.getTransactionId(), charge, CAPTURED, expectedNotification.getGatewayEventDate());
    }

    @Test
    void givenAChargeCapturedNotification_shouldInvokeChargeNotificationProcessor_IfChargeIsHistoric() {
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build());
        charge.setHistoric(true);
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountServiceToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "",
                "CAPTURED",
                "10",
                "03",
                "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertTrue(result);
        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(gatewayAccountEntity, transactionId, charge, CAPTURED);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void givenARefundNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountServiceToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        final List<String> refundSuccessStatuses = Arrays.asList(
                "REFUNDED",
                "REFUNDED_BY_MERCHANT"
        );

        for (String status : refundSuccessStatuses) {
            final String payload = sampleWorldpayNotification(
                    transactionId, referenceId, "", status,
                    "10", "03", "2017");

            final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
            assertTrue(result);
        }

        verifyNoInteractions(mockChargeNotificationProcessor);
        verify(mockRefundNotificationProcessor, times(2)).invoke(WORLDPAY, RefundStatus.REFUNDED,
                gatewayAccountEntity, referenceId, transactionId, charge);
    }

    @Test
    void givenARefundFailedNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountServiceToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        final String payload = sampleWorldpayNotification(
                transactionId, referenceId, "", "REFUND_FAILED",
                "10", "03", "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertTrue(result);
        verifyNoInteractions(mockChargeNotificationProcessor);
        verify(mockRefundNotificationProcessor).invoke(WORLDPAY, RefundStatus.REFUND_ERROR,
                gatewayAccountEntity, referenceId, transactionId, charge);
    }

    @Test
    void ifChargeNotFound_shouldRejectNotificationForTelephonePaymentsAccount() {
        when(mockGatewayAccountService.isATelephonePaymentNotificationAccount("MERCHANTCODE")).thenReturn(true);
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "",
                "CHARGED",
                "10",
                "03",
                "2017");
        setUpChargeServiceToReturnCharge(Optional.empty());

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertFalse(result);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    void ifChargeNotFound_shouldIgnoreForNonTelephonePaymentAccount() {
        when(mockGatewayAccountService.isATelephonePaymentNotificationAccount("MERCHANTCODE")).thenReturn(false);
        final String payload = sampleWorldpayNotification(
                transactionId, referenceId, "", "CHARGED", "10", "03", "2017");
        setUpChargeServiceToReturnCharge(Optional.empty());

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertTrue(result);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    void ifGatewayAccountNotFound_shouldNotInvokeChargeNotificationProcessorAndReturnFalse() {
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountServiceToReturnGatewayAccountEntity(Optional.empty());
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "",
                "CHARGED",
                "10",
                "03",
                "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertFalse(result);
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifTransactionIdEmpty_shouldNotInvokeChargeNotificationProcessor() {
        final String payload = sampleWorldpayNotification(
                "",
                referenceId,
                "",
                "CHARGED",
                "10",
                "03",
                "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertTrue(result);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    void shouldIgnoreNotificationWhenStatusIsToBeIgnored() {
        final List<String> ignoredStatuses = Arrays.asList(
                "SENT_FOR_AUTHORISATION",
                "AUTHORISED",
                "CANCELLED",
                "EXPIRED",
                "REFUSED",
                "REFUSED_BY_BANK",
                "SETTLED",
                "SETTLED_BY_MERCHANT",
                "SENT_FOR_REFUND"
        );

        for (String status : ignoredStatuses) {
            final String payload = sampleWorldpayNotification(
                    transactionId,
                    referenceId,
                    refundAuthorisationReference,
                    status);

            assertTrue(notificationService.handleNotificationFor(ipAddress, payload));

            verifyNoInteractions(mockChargeNotificationProcessor);
            verifyNoInteractions(mockRefundNotificationProcessor);
        }
    }

    @Test
    void shouldCheckGatewayAccountTypeAndReturnTrue_forErrorNotification() {
        GatewayAccountEntity mockGatewayAccountEntity = Mockito.mock(GatewayAccountEntity.class);
        when(mockGatewayAccountEntity.isLive()).thenReturn(true);
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountServiceToReturnGatewayAccountEntity(Optional.of(mockGatewayAccountEntity));

        final String payload = sampleWorldpayNotification(transactionId, referenceId, "", "ERROR");

        assertTrue(notificationService.handleNotificationFor(ipAddress, payload));
        verify(mockGatewayAccountEntity, times(1)).isLive();
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifIpAddressOutsidePermittedRange_returnFalseAndDontProcessNotification() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "",
                "CAPTURED",
                "10",
                "03",
                "2017");
        when(mockWorldpayConfiguration.isNotificationEndpointSecured()).thenReturn(true);
        when(mockWorldpayConfiguration.getNotificationDomain()).thenReturn("worldpay.com");
        when(mockIpDomainMatcher.ipMatchesDomain(ipAddress, "worldpay.com")).thenReturn(false);

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);

        assertFalse(result);
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifPayloadNotValidXml_shouldIgnoreNotification() {
        String payload = "<not></valid>";

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertTrue(result);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    private static String sampleWorldpayNotification(
            String transactionId,
            String referenceId,
            String refundAuthorisationReference,
            String status,
            String bookingDateDay,
            String bookingDateMonth,
            String bookingDateYear) {
        return TestTemplateResourceLoader.load(WORLDPAY_NOTIFICATION)
                .replace("{{transactionId}}", transactionId)
                .replace("{{refund-ref}}", referenceId)
                .replace("{{refund-authorisation-reference}}", refundAuthorisationReference)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", bookingDateDay)
                .replace("{{bookingDateMonth}}", bookingDateMonth)
                .replace("{{bookingDateYear}}", bookingDateYear);
    }

    private static String sampleWorldpayNotification(
            String transactionId,
            String referenceId,
            String refundAuthorisationReference,
            String status) {
        return sampleWorldpayNotification(transactionId, referenceId, refundAuthorisationReference, status, "2017", "10", "22");
    }

    private void setUpGatewayAccountServiceToReturnGatewayAccountEntity(Optional<GatewayAccountEntity> gatewayAccountEntity) {
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(gatewayAccountEntity);
    }

    private void setUpChargeServiceToReturnCharge(Optional<Charge> charge) {
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(WORLDPAY.getName(), transactionId)).thenReturn(charge);
    }
}
