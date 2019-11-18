package uk.gov.pay.connector.gateway.stripe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.UNKNOWN;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_3DS_SOURCE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_ACCOUNT_UPDATED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT;

@RunWith(MockitoJUnitRunner.class)
public class StripeNotificationServiceTest {
    private StripeNotificationService notificationService;

    @Mock
    private Card3dsResponseAuthService mockCard3dsResponseAuthService;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private ChargeEntity mockCharge;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;
    
    private StripeAccountUpdatedHandler stripeAccountUpdatedHandler = new StripeAccountUpdatedHandler(new ObjectMapper());
    
    private final String externalId = "external-id";
    private final String sourceId = "source-id";
    private final String webhookTestSigningSecret = "whtest";
    private final String webhookLiveSigningSecret = "whlive";

    @Before
    public void setup() {
        notificationService = new StripeNotificationService(mockCard3dsResponseAuthService,
                mockChargeService, stripeGatewayConfig, stripeAccountUpdatedHandler);

        when(stripeGatewayConfig.getWebhookSigningSecrets()).thenReturn(List.of(webhookLiveSigningSecret, webhookTestSigningSecret));
        when(mockCharge.getExternalId()).thenReturn(externalId);
        when(mockCharge.getStatus()).thenReturn(AUTHORISATION_3DS_REQUIRED.getValue());
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), sourceId)).thenReturn(Optional.of(mockCharge));
    }

    private String signPayload(String payload) {
        return StripeNotificationUtilTest.generateSigHeader(webhookLiveSigningSecret, payload);
    }

    private String signPayloadWithTestSecret(String payload) {
        return StripeNotificationUtilTest.generateSigHeader(webhookTestSigningSecret, payload);
    }

    @Test
    public void should_log_the_requirements_and_payoutsDisabled_json_when_an_account_updated_event_is_received() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeAccountUpdatedHandler.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
        
        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_ACCOUNT_UPDATED);
        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getMessage()).contains("Received an account.updated event for stripe account");
        assertThat(loggingEvent.getArgumentArray()).hasSize(3);
    }
    
    @Test
    public void shouldUpdateCharge_WhenNotificationIsFor3DSSourceChargeable() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_CHARGEABLE);

        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsDetails(Auth3dsDetails.Auth3dsResult.AUTHORISED));
    }

    @Test
    public void shouldUpdateCharge_WhenNotificationIsFor3DSSourceChargeable_withTestWebhook() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_CHARGEABLE);

        notificationService.handleNotificationFor(payload, signPayloadWithTestSecret(payload));

        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsDetails(Auth3dsDetails.Auth3dsResult.AUTHORISED));
    }

    @Test
    public void shouldUpdateCharge_WhenNotificationIsFor3DSSourceFailed() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_FAILED);
        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsDetails(Auth3dsDetails.Auth3dsResult.DECLINED));
    }

    @Test
    public void shouldUpdateCharge_WhenNotificationIsFor3DSSourceCancelled() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_CANCELED);
        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsDetails(Auth3dsDetails.Auth3dsResult.CANCELED));
    } 
    
    @Test
    public void shouldUpdateCharge_WhenNotificationIsForPaymentIntentAmountCapturableUpdated() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                "pi_123", PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);
        when(mockCharge.getAmount()).thenReturn(1000L);
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), "pi_123")).thenReturn(Optional.of(mockCharge));
        
        notificationService.handleNotificationFor(payload, signPayload(payload));
        
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsDetails(Auth3dsDetails.Auth3dsResult.AUTHORISED));
    }
    
    @Test
    public void shouldNotUpdateCharge_WhenNotificationIsForPaymentIntentAmountCapturableUpdatedButAmountsDontMatch() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                "pi_123", PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);
        when(mockCharge.getAmount()).thenReturn(500L);
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), "pi_123")).thenReturn(Optional.of(mockCharge));
        
        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(any(), any());
    }

    @Test
    public void shouldUpdateCharge_WhenNotificationIsForPaymentIntentPaymentFailed() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                "pi_123", PAYMENT_INTENT_PAYMENT_FAILED);

        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), "pi_123")).thenReturn(Optional.of(mockCharge));
        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsDetails(Auth3dsDetails.Auth3dsResult.DECLINED));
    }

    @Test
    public void shouldNotUpdate_IfChargeIsNotIn3dsReadyForASourceNotification() {

        final List<StripeNotificationType> sourceTypes = ImmutableList.of(
                SOURCE_CANCELED, SOURCE_CHARGEABLE, SOURCE_FAILED);

        when(mockCharge.getStatus()).thenReturn(ENTERING_CARD_DETAILS.getValue());
        for (StripeNotificationType type : sourceTypes) {
            final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE, sourceId, type);
            notificationService.handleNotificationFor(payload, signPayload(payload));
        }

        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, UNKNOWN);

        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    public void shouldNotUpdateCharge_WhenTransactionIdIsNotAvailable() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                StringUtils.EMPTY, SOURCE_CHARGEABLE);

        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    public void shouldNotUpdateCharge_WhenChargeIsNotFoundForTransactionId() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                "unknown-source-id", SOURCE_CHARGEABLE);

        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    public void shouldNotUpdateCharge_WhenPayloadIsInvalid() {
        final String payload = "invalid-payload";
        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test(expected = WebApplicationException.class)
    public void shouldThrowException_WhenSignatureIsInvalid() {
        final String payload = "invalid-payload";
        notificationService.handleNotificationFor(payload, "invalid-signature");
    }

    private static String sampleStripeNotification(String location,
                                                   String sourceId,
                                                   StripeNotificationType stripeNotificationType) {
        return TestTemplateResourceLoader.load(location)
                .replace("{{id}}", sourceId)
                .replace("{{type}}", stripeNotificationType.getType());
    }

    private Auth3dsDetails getAuth3dsDetails(Auth3dsDetails.Auth3dsResult auth3dsResult) {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setAuth3dsResult(auth3dsResult.toString());
        return auth3dsDetails;
    }
}
