package uk.gov.pay.connector.gateway.worldpay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayNotificationServiceTest {
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
    private final String transactionId = "transaction-reference";

    @Before
    public void setup() {
        notificationService = new WorldpayNotificationService(
                mockChargeService,
                mockWorldpayConfiguration,
                mockIpDomainMatcher,
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor,
                mockGatewayAccountService);
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(WORLDPAY.getName(), transactionId)).thenReturn(Optional.of(charge));
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
    }

    @Test
    public void givenAChargeCapturedNotification_chargeNotificationProcessorInvokedWithNotificationAndCharge() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
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
                referenceId
        );
        verify(mockChargeNotificationProcessor).invoke(expectedNotification.getTransactionId(), charge, CAPTURED, expectedNotification.getGatewayEventDate());
    }
    @Test
    public void givenAChargeCapturedNotification_shouldNotInvokeChargeNotificationProcessor_IfChargeIsHistoric() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "CAPTURED",
                "10",
                "03",
                "2017");

        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build());
        charge.setHistoric(true);

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(WORLDPAY.getName(), transactionId)).thenReturn(Optional.of(charge));

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertFalse(result);
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    public void givenARefundNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        final List<String> refundSuccessStatuses = Arrays.asList(
                "REFUNDED",
                "REFUNDED_BY_MERCHANT"
        );

        for (String status : refundSuccessStatuses) {
            final String payload = sampleWorldpayNotification(
                    transactionId, referenceId, status,
                    "10", "03", "2017");

            final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
            assertTrue(result);
        }

        verifyNoInteractions(mockChargeNotificationProcessor);
        verify(mockRefundNotificationProcessor, times(2)).invoke(WORLDPAY, RefundStatus.REFUNDED,
                gatewayAccountEntity, referenceId, transactionId, charge);
    }

    @Test
    public void givenARefundFailedNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        final String payload = sampleWorldpayNotification(
                transactionId, referenceId, "REFUND_FAILED",
                "10", "03", "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertTrue(result);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verify(mockRefundNotificationProcessor).invoke(WORLDPAY, RefundStatus.REFUND_ERROR,
                gatewayAccountEntity, referenceId, transactionId, charge);
    }

    @Test
    public void ifChargeNotFound_shouldNotInvokeChargeNotificationProcessorAndReturnFalse() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "CHARGED",
                "10",
                "03",
                "2017");

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(WORLDPAY.getName(), 
                transactionId)).thenReturn(Optional.empty());

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertFalse(result);

        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    public void ifGatewayAccountNotFound_shouldNotInvokeChargeNotificationProcessorAndReturnFalse() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "CHARGED",
                "10",
                "03",
                "2017");

        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.empty());

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertFalse(result);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    public void ifTransactionIdEmpty_shouldNotInvokeChargeNotificationProcessor() {
        final String payload = sampleWorldpayNotification(
                "",
                referenceId,
                "CHARGED",
                "10",
                "03",
                "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertTrue(result);

        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsToBeIgnored() {
        final List<String> ignoredStatuses = Arrays.asList(
                "SENT_FOR_AUTHORISATION",
                "AUTHORISED",
                "CANCELLED",
                "EXPIRED",
                "REFUSED",
                "REFUSED_BY_BANK",
                "SETTLED_BY_MERCHANT",
                "SENT_FOR_REFUND"
        );

        for (String status : ignoredStatuses) {
            final String payload = sampleWorldpayNotification(
                    transactionId,
                    referenceId,
                    status);

            assertTrue(notificationService.handleNotificationFor(ipAddress, payload));

            verifyNoInteractions(mockChargeNotificationProcessor);
            verifyNoInteractions(mockRefundNotificationProcessor);
        }
    }

    @Test
    public void ifIpAddressOutsidePermittedRange_returnFalseAndDontProcessNotification() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
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
    public void ifPayloadNotValidXml_shouldIgnoreNotification() {
        String payload = "<not></valid>";

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertTrue(result);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    private static String sampleWorldpayNotification(
            String transactionId,
            String referenceId,
            String status,
            String bookingDateDay,
            String bookingDateMonth,
            String bookingDateYear) {
        return TestTemplateResourceLoader.load(WORLDPAY_NOTIFICATION)
                .replace("{{transactionId}}", transactionId)
                .replace("{{refund-ref}}", referenceId)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", bookingDateDay)
                .replace("{{bookingDateMonth}}", bookingDateMonth)
                .replace("{{bookingDateYear}}", bookingDateYear);
    }

    private static String sampleWorldpayNotification(
            String transactionId,
            String referenceId,
            String status) {
        return sampleWorldpayNotification(transactionId, referenceId, status, "2017", "10", "22");
    }
}
