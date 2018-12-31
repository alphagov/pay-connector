package uk.gov.pay.connector.gateway.worldpay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DnsUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayNotificationServiceTest {
    private WorldpayNotificationService notificationService;

    @Mock
    private ChargeDao mockChargeDao;
    @Mock
    private WorldpayNotificationConfiguration mockWorldpayConfiguration;
    @Mock
    private DnsUtils mockDnsUtils;
    @Mock
    private ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock
    private RefundNotificationProcessor mockRefundNotificationProcessor;
    @Mock
    private ChargeEntity mockCharge;

    private final String ipAddress = "1.1.1.1";
    private final String referenceId = "refund-reference";
    private final String transactionId = "transaction-reference";

    @Before
    public void setup() {
        notificationService = new WorldpayNotificationService(
                mockChargeDao,
                mockWorldpayConfiguration,
                mockDnsUtils,
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor
        );
        when(mockChargeDao.findByProviderAndTransactionId(WORLDPAY.getName(), transactionId)).thenReturn(Optional.of(mockCharge));
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
        assertEquals(true, result);

        WorldpayNotification expectedNotification = new WorldpayNotification(
                "MERCHANTCODE",
                "CAPTURED",
                10,
                3,
                2017,
                transactionId,
                referenceId
        );
        verify(mockChargeNotificationProcessor).invoke(expectedNotification.getTransactionId(), mockCharge, CAPTURED, expectedNotification.getGatewayEventDate());
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
            assertEquals(true, result);
        }


        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, times(2)).invoke(WORLDPAY, RefundStatus.REFUNDED, referenceId, transactionId);
    }

    @Test
    public void givenARefundFailedNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        final String payload = sampleWorldpayNotification(
                transactionId, referenceId, "REFUND_FAILED",
                "10", "03", "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertEquals(true, result);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor).invoke(WORLDPAY, RefundStatus.REFUND_ERROR, referenceId, transactionId);
    }


    @Test
    public void ifChargeNotFound_shouldNotInvokeChargeNotificationProcessor() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "CHARGED",
                "10",
                "03",
                "2017");

        when(mockChargeDao.findByProviderAndTransactionId(WORLDPAY.getName(), transactionId)).thenReturn(Optional.empty());

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertEquals(result, true);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
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
        assertEquals(result, true);

        verify(mockChargeNotificationProcessor, never()).invoke(anyString(), any(), any(), any());
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

            assertEquals(true, notificationService.handleNotificationFor(ipAddress, payload));

            verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
            verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
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
        when(mockDnsUtils.ipMatchesDomain(ipAddress, "worldpay.com")).thenReturn(false);

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertEquals(false, result);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void ifPayloadNotValidXml_shouldIgnoreNotification() {
        String payload = "<not></valid>";

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertEquals(true, result);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
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
