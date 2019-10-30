package uk.gov.pay.connector.gateway.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
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
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_MULTIPLE_NOTIFICATIONS_DIFFERENT_DATES;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_NOTIFICATION_AUTHORISATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_NOTIFICATION_CAPTURE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_NOTIFICATION_CAPTURE_WITH_UNKNOWN_STATUS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_NOTIFICATION_REFUND;
import static uk.gov.pay.connector.util.TransactionId.randomId;

@RunWith(MockitoJUnitRunner.class)
public class SmartpayNotificationServiceTest {
    private SmartpayNotificationService notificationService;

    @Mock
    private ChargeDao mockChargeDao;
    @Mock
    private ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock
    private RefundNotificationProcessor mockRefundNotificationProcessor;
    @Mock
    private ChargeEntity mockCharge;

    private final String originalReference = "original-reference";
    private final String pspReference = "psp-reference";

    @Before
    public void setup() {
        ObjectMapper objectMapper = new ObjectMapper();

        notificationService = new SmartpayNotificationService(
                mockChargeDao,
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor
        );
        when(mockCharge.getStatus()).thenReturn(AUTHORISATION_SUCCESS.getValue());

        when(mockChargeDao.findByProviderAndTransactionId(SMARTPAY.getName(), originalReference)).thenReturn(Optional.of(mockCharge));
    }

    @Test
    public void shouldUpdateCharge_WhenNotificationIsForChargeCapture() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_CAPTURE,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload, null);

        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockChargeNotificationProcessor).invoke(originalReference, mockCharge, CAPTURED,
                ZonedDateTime.parse("2015-10-08T13:48:30+02:00"));  // from notification-capture.json
    }

    @Test
    public void shouldUpdateRefund_WhenNotificationIsForRefund() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_REFUND,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload, null);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor).invoke(SMARTPAY,
                RefundStatus.REFUNDED, pspReference, originalReference);
    }

    @Test
    public void shouldIgnore_WhenNotificationIsForAuthorisation() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_AUTHORISATION,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload, null);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldProcessMultipleNotifications() {
        final String payload = sampleSmartpayNotification(SMARTPAY_MULTIPLE_NOTIFICATIONS_DIFFERENT_DATES,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload, null);

        verify(mockChargeNotificationProcessor, times(1)).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_CAPTURE_WITH_UNKNOWN_STATUS,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload, null);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateChargeOrRefund_WhenTransactionIdIsNotAvailable() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_REFUND,
                randomId(), originalReference, StringUtils.EMPTY);

        notificationService.handleNotificationFor(payload, null);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateChargeOrRefund_WhenChargeIsNotFoundForTransactionId() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_REFUND,
                randomId(), "unknown-transaction-id", pspReference);

        notificationService.handleNotificationFor(payload, null);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateChargeOrRefund_WhenPayloadIsInvalid() {
        final String payload = "invalid-payload";

        notificationService.handleNotificationFor(payload, null);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    private static String sampleSmartpayNotification(String location,
                                                     String merchantReference,
                                                     String originalReference,
                                                     String pspReference) {
        return TestTemplateResourceLoader.load(location)
                .replace("{{merchantReference}}", merchantReference)
                .replace("{{originalReference}}", originalReference)
                .replace("{{transactionId}}", originalReference)
                .replace("{{transactionId2}}", originalReference)
                .replace("{{originalReference}}", originalReference)
                .replace("{{pspReference}}", pspReference);
    }
}
