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
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
    protected ChargeDao mockChargeDao;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    protected UserNotificationService mockUserNotificationService;
    @Mock
    protected ChargeNotificationProcessor mockChargeNotificationProcessor;

    RefundNotificationProcessor mockRefundNotificationProcessor;
    @Mock
    protected ChargeEntity mockCharge;
    @Mock
    private RefundEntity mockRefund;
    @Mock
    protected GatewayAccountEntity mockGatewayAccountEntity;

    private final String originalReference = "original-reference";
    private final String pspReference = "psp-reference";

    @Before
    public void setup() {
        ObjectMapper objectMapper = new ObjectMapper();
        mockRefundNotificationProcessor = spy(new RefundNotificationProcessor(mockRefundDao, mockUserNotificationService));

        notificationService = new SmartpayNotificationService(
                mockChargeDao,
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor
        );
        when(mockCharge.getStatus()).thenReturn(AUTHORISATION_SUCCESS.getValue());

        when(mockChargeDao.findByProviderAndTransactionId(SMARTPAY.getName(), originalReference)).thenReturn(Optional.of(mockCharge));
        when(mockRefundDao.findByProviderAndReference(SMARTPAY.getName(), pspReference)).thenReturn(Optional.of(mockRefund));
        when(mockRefund.getChargeEntity()).thenReturn(mockCharge);
        when(mockCharge.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
    }

    @Test
    public void shouldUpdateCharge_WhenNotificationIsForChargeCapture() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_CAPTURE,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload);

        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockChargeNotificationProcessor).invoke(pspReference, mockCharge, CAPTURED,
                ZonedDateTime.parse("2015-10-08T13:48:30+02:00"));  // from notification-capture.json
    }

    @Test
    public void shouldUpdateRefund_WhenNotificationIsForRefund() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_REFUND,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor).invoke(SMARTPAY,
                RefundStatus.REFUNDED, pspReference, originalReference);
        verify(mockUserNotificationService).sendRefundIssuedEmail(any());
    }

    @Test
    public void shouldIgnore_WhenNotificationIsForAuthorisation() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_AUTHORISATION,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldProcessMultipleNotifications() {
        final String payload = sampleSmartpayNotification(SMARTPAY_MULTIPLE_NOTIFICATIONS_DIFFERENT_DATES,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, times(1)).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_CAPTURE_WITH_UNKNOWN_STATUS,
                randomId(), originalReference, pspReference);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateChargeOrRefund_WhenTransactionIdIsNotAvailable() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_REFUND,
                randomId(), originalReference, StringUtils.EMPTY);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateChargeOrRefund_WhenChargeIsNotFoundForTransactionId() {
        final String payload = sampleSmartpayNotification(SMARTPAY_NOTIFICATION_REFUND,
                randomId(), "unknown-transaction-id", pspReference);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateChargeOrRefund_WhenPayloadIsInvalid() {
        final String payload = "invalid-payload";

        notificationService.handleNotificationFor(payload);

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
