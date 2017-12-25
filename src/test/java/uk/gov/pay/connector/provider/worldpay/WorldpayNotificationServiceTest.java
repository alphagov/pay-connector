package uk.gov.pay.connector.provider.worldpay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.service.worldpay.WorldpayNotification;
import uk.gov.pay.connector.util.DnsUtils;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.service.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.sampleWorldpayNotification;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayNotificationServiceTest {
    private WorldpayNotificationService notificationService;

    @Mock private ChargeDao mockChargeDao;
    @Mock private WorldpayNotificationConfiguration mockWorldpayConfiguration;
    @Mock private DnsUtils mockDnsUtils;
    @Mock private ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock private RefundNotificationProcessor mockRefundNotificationProcessor;
    @Mock private ChargeEntity mockCharge;

    private final String ipAddress = "1.1.1.1";
    private final String referenceId = "refund-reference";
    private final String transactionId = "transaction-reference";

    @Before
    public void setup() throws Exception {
        notificationService = new WorldpayNotificationService(
                mockChargeDao,
                mockWorldpayConfiguration,
                mockDnsUtils,
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor
        );
        when(mockChargeDao.findByProviderAndTransactionId(WORLDPAY.getName(), transactionId)).thenReturn(Optional.of(mockCharge));
        when(mockChargeNotificationProcessor.willHandle(any())).thenCallRealMethod();
        when(mockRefundNotificationProcessor.willHandle(any())).thenCallRealMethod();
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
                03,
                2017,
                transactionId,
                referenceId
        );
        verify(mockChargeNotificationProcessor).invoke(expectedNotification, mockCharge);
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any());
    }

    @Test
    public void givenARefundNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        final String payload = sampleWorldpayNotification(
                transactionId,
                referenceId,
                "REFUNDED",
                "10",
                "03",
                "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertEquals(true, result);

        WorldpayNotification expectedNotification = new WorldpayNotification(
                "MERCHANTCODE",
                "REFUNDED",
                10,
                3,
                2017,
                transactionId,
                referenceId
        );
        verify(mockChargeNotificationProcessor, never()).invoke(any(), any());
        verify(mockRefundNotificationProcessor).invoke(expectedNotification, mockCharge);
    }

    @Test
    public void ifChargeNotFound_returnsTrueAndCommandNotInvoked() {
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

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any());
    }

    @Test
    public void ifTransactionIdEmpty_chargeProcessorNotInvoked() {
        final String payload = sampleWorldpayNotification(
                "",
                referenceId,
                "CHARGED",
                "10",
                "03",
                "2017");

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertEquals(result, true);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any());
    }

    @Test
    public void ignoredNotificationsAreIgnored() {
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

            verify(mockChargeNotificationProcessor, never()).invoke(any(), any());
            verify(mockRefundNotificationProcessor, never()).invoke(any(), any());
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

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any());
    }

    @Test
    public void ifPayloadNotValidXml_shouldIgnoreNotificatoin() {
        String payload = "<not></valid>";

        final boolean result = notificationService.handleNotificationFor(ipAddress, payload);
        assertEquals(true, result);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any());
    }
}
