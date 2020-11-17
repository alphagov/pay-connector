package uk.gov.pay.connector.gateway.stripe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import net.logstash.logback.marker.ObjectAppendingMarker;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.payout.PayoutEmitterService;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.payout.Payout;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;
import uk.gov.pay.connector.util.IpAddressMatcher;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.WebApplicationException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_CREATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.UNKNOWN;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.byType;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_3DS_SOURCE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_ACCOUNT_UPDATED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYOUT_NOTIFICATION;

@ExtendWith(MockitoExtension.class)
class StripeNotificationServiceTest {
    private static final String FORWARDED_IP_ADDRESSES = "1.2.3.4, 102.108.0.6";
    private static final List<String> ALLOWED_IP_ADDRESSES = List.of("1.2.3.4", "9.9.9.9");

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
    private PayoutReconcileQueue mockPayoutReconcileQueue;
    @Mock
    private PayoutEmitterService mockPayoutEmitterService;
    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Captor
    private ArgumentCaptor<Payout> payoutArgumentCaptor;

    private StripeAccountUpdatedHandler stripeAccountUpdatedHandler = new StripeAccountUpdatedHandler(new ObjectMapper());

    private final String externalId = "external-id";
    private final String sourceId = "source-id";
    private final String webhookTestSigningSecret = "whtest";
    private final String webhookLiveSigningSecret = "whlive";

    @BeforeEach
    void setup() {
        notificationService = new StripeNotificationService(
                mockCard3dsResponseAuthService,
                mockChargeService,
                stripeGatewayConfig,
                stripeAccountUpdatedHandler,
                mockPayoutReconcileQueue,
                mockPayoutEmitterService,
                new IpAddressMatcher());

        lenient().when(stripeGatewayConfig.getWebhookSigningSecrets()).thenReturn(List.of(webhookLiveSigningSecret, webhookTestSigningSecret));
        when(stripeGatewayConfig.getAllowedIpAddresses()).thenReturn(ALLOWED_IP_ADDRESSES);
    }

    private void setUpCharge() {
        when(mockCharge.getExternalId()).thenReturn(externalId);
        when(mockCharge.getStatus()).thenReturn(AUTHORISATION_3DS_REQUIRED.getValue());
    }

    private void setUpChargeServiceToReturnCharge() {
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), sourceId)).thenReturn(Optional.of(mockCharge));
    }

    private String signPayload(String payload) {
        return StripeNotificationUtilTest.generateSigHeader(webhookLiveSigningSecret, payload);
    }

    private String signPayloadWithTestSecret(String payload) {
        return StripeNotificationUtilTest.generateSigHeader(webhookTestSigningSecret, payload);
    }

    @Test
    void should_log_the_requirements_and_payoutsDisabled_json_when_an_account_updated_event_is_received() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeAccountUpdatedHandler.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_ACCOUNT_UPDATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getMessage(), containsString("Received an account.updated event for stripe account"));
        assertThat(loggingEvent.getArgumentArray().length, is(3));
    }

    @Test
    void shouldLogTheIdOfThePayoutCreatedEvent_whenItIsReceived() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = sampleStripeNotification(STRIPE_PAYOUT_NOTIFICATION,
                "evt_id", PAYOUT_CREATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(3)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getMessage(),
                containsString("Processing stripe payout created notification with id [evt_aaaaaaaaaaaaaaaaaaaaa]"));

        Object[] arguments = loggingEvent.getArgumentArray();
        assertThat(arguments.length, is(1));
        assertThat(arguments, hasItemInArray(new ObjectAppendingMarker("stripe_connect_account_id", "connect_account_id")));
    }

    @Test
    void shouldSendThePayoutCreatedEventToPayoutReconcileQueue() throws QueueException, JsonProcessingException {
        String payload = sampleStripeNotification(STRIPE_PAYOUT_NOTIFICATION,
                "evt_id", PAYOUT_CREATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockPayoutEmitterService, never()).emitPayoutEvent(any(), any(), any(), any());

        verify(mockPayoutReconcileQueue).sendPayout(payoutArgumentCaptor.capture());
        Payout payout = payoutArgumentCaptor.getValue();
        assertThat(payout.getGatewayPayoutId(), is("po_aaaaaaaaaaaaaaaaaaaaa"));
        assertThat(payout.getConnectAccountId(), is("connect_account_id"));
        assertThat(payout.getCreatedDate(), is(ZonedDateTime.parse("2020-03-24T01:30:46Z")));
    }

    @Test
    void shouldSendAllExceptPayoutCreatedEventToEventQueue() {
        List<String> types = List.of("payout.updated", "payout.paid", "payout.failed");
        StripePayout payout = new StripePayout("po_aaaaaaaaaaaaaaaaaaaaa", 1337702L, 1585094400L,
                1585013446L, "in_transit", "bank_account", null);

        types.forEach(type -> {
            StripeNotificationType stripeNotificationType = byType(type);
            String payload = sampleStripeNotification(STRIPE_PAYOUT_NOTIFICATION,
                    "evt_id", stripeNotificationType);

            assertTrue(notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES));

            verify(mockPayoutEmitterService).emitPayoutEvent(stripeNotificationType.getEventClass().get(),
                    toUTCZonedDateTime(1567622603L), "connect_account_id", payout);

            reset(mockPayoutEmitterService);
        });
    }

    @Test
    void shouldLogErrorIfPayoutCouldNotBeSentToPayoutReconcileQueue() throws QueueException, JsonProcessingException {
        String payload = sampleStripeNotification(STRIPE_PAYOUT_NOTIFICATION,
                "evt_id", PAYOUT_CREATED);
        Logger root = (Logger) LoggerFactory.getLogger(StripeNotificationService.class);
        root.setLevel(Level.ERROR);
        root.addAppender(mockAppender);
        doThrow(new QueueException("Failed to send to queue")).when(mockPayoutReconcileQueue).sendPayout(any());

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getMessage(),
                containsString("Error sending payout to payout reconcile queue: exception [Failed to send to queue]"));

        Object[] arguments = loggingEvent.getArgumentArray();
        assertThat(arguments.length, is(2));
        assertThat(arguments, hasItemInArray(new ObjectAppendingMarker("stripe_connect_account_id", "connect_account_id")));
        assertThat(arguments, hasItemInArray(new ObjectAppendingMarker("gateway_payout_id", "po_aaaaaaaaaaaaaaaaaaaaa")));
    }

    @Test
    void shouldUpdateCharge_WhenNotificationIsFor3DSSourceChargeable() {
        setUpCharge();
        setUpChargeServiceToReturnCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_CHARGEABLE);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED));
    }

    @Test
    void shouldUpdateCharge_WhenNotificationIsFor3DSSourceChargeable_withTestWebhook() {
        setUpCharge();
        setUpChargeServiceToReturnCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_CHARGEABLE);

        final boolean result = notificationService.handleNotificationFor(payload, signPayloadWithTestSecret(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED));
    }

    @Test
    void shouldUpdateCharge_WhenNotificationIsFor3DSSourceFailed() {
        setUpCharge();
        setUpChargeServiceToReturnCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_FAILED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.DECLINED));
    }

    @Test
    void shouldUpdateCharge_WhenNotificationIsFor3DSSourceCancelled() {
        setUpCharge();
        setUpChargeServiceToReturnCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_CANCELED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.CANCELED));
    }

    @Test
    void shouldUpdateCharge_WhenNotificationIsForPaymentIntentAmountCapturableUpdated() {
        setUpCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                "pi_123", PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);
        when(mockCharge.getAmount()).thenReturn(1000L);
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), "pi_123")).thenReturn(Optional.of(mockCharge));

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED));
    }

    @Test
    void shouldNotUpdateCharge_WhenNotificationIsForPaymentIntentAmountCapturableUpdatedButAmountsDontMatch() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                "pi_123", PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);
        when(mockCharge.getAmount()).thenReturn(500L);
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), "pi_123")).thenReturn(Optional.of(mockCharge));

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(any(), any());
    }

    @Test
    void shouldUpdateCharge_WhenNotificationIsForPaymentIntentPaymentFailed() {
        setUpCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                "pi_123", PAYMENT_INTENT_PAYMENT_FAILED);
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), "pi_123")).thenReturn(Optional.of(mockCharge));

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.DECLINED));
    }

    @Test
    void shouldNotUpdate_IfChargeIsNotIn3dsReadyForASourceNotification() {
        final List<StripeNotificationType> sourceTypes = ImmutableList.of(
                SOURCE_CANCELED, SOURCE_CHARGEABLE, SOURCE_FAILED);

        for (StripeNotificationType type : sourceTypes) {
            final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE, sourceId, type);
            assertTrue(notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES));
        }

        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, UNKNOWN);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    void shouldNotUpdateCharge_WhenTransactionIdIsNotAvailable() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                StringUtils.EMPTY, SOURCE_CHARGEABLE);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    void shouldNotUpdateCharge_WhenChargeIsNotFoundForTransactionId() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                "unknown-source-id", SOURCE_CHARGEABLE);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    void shouldNotUpdateCharge_WhenPayloadIsInvalid() {
        final String payload = "invalid-payload";
        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    void shouldWaitForSpecifiedDelayBeforeProcessingNotification() {
        setUpCharge();
        setUpChargeServiceToReturnCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_FAILED);
        when(stripeGatewayConfig.getNotification3dsWaitDelay()).thenReturn(1000);
        when(mockChargeService.findChargeByExternalId(anyString())).thenReturn(mockCharge);

        Instant instantBeforeInvocation = Instant.now();
        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);
        Instant instantAfterInvocation = Instant.now();
        assertTrue(instantAfterInvocation.isAfter(instantBeforeInvocation.plusSeconds(1)));
        assertTrue(instantAfterInvocation.isBefore(instantBeforeInvocation.plusMillis(1500))); //includes additional overhead to complete handleNotificationFor() 

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.DECLINED));
    }

    @Test
    void shouldNotWaitForSpecifiedDelayIfChargeIs3DSReady() {
        setUpCharge();
        setUpChargeServiceToReturnCharge();
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_FAILED);
        when(stripeGatewayConfig.getNotification3dsWaitDelay()).thenReturn(2000);
        when(mockChargeService.findChargeByExternalId(anyString())).thenReturn(mockCharge);
        when(mockCharge.getStatus()).thenReturn(AUTHORISATION_3DS_READY.getValue());

        Instant instantBeforeInvocation = Instant.now();
        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);
        Instant instantAfterInvocation = Instant.now();
        assertTrue(instantAfterInvocation.isBefore(instantBeforeInvocation.plusMillis(300))); // plus 300 to consider the time to process handleNotificationFor()

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.DECLINED));
    }

    @Test
    void shouldThrowException_WhenSignatureIsInvalid() {
        final String payload = "invalid-payload";

        assertThrows(WebApplicationException.class,
                () -> notificationService.handleNotificationFor(payload, "invalid-signature", FORWARDED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenForwardedIpAddressIsNotInAllowedIpAddresses() {
        String forwardedIpAddresses = "1.1.1.1, 102.108.0.6";
        String payload = sampleStripeNotification(STRIPE_PAYOUT_NOTIFICATION,
                "evt_id", PAYOUT_CREATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), forwardedIpAddresses);

        assertFalse(result);
    }

    private static String sampleStripeNotification(String location,
                                                   String eventId,
                                                   StripeNotificationType stripeNotificationType) {
        return TestTemplateResourceLoader.load(location)
                .replace("{{id}}", eventId)
                .replace("{{type}}", stripeNotificationType.getType());
    }

    private Auth3dsResult getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome auth3dsResultOutcome) {
        Auth3dsResult auth3dsResult = new Auth3dsResult();
        auth3dsResult.setAuth3dsResult(auth3dsResultOutcome.toString());
        return auth3dsResult;
    }
}
