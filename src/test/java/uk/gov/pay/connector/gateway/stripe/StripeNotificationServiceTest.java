package uk.gov.pay.connector.gateway.stripe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.marker.ObjectAppendingMarker;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.payout.PayoutEmitterService;
import uk.gov.pay.connector.queue.payout.Payout;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.util.CidrUtils;
import uk.gov.pay.connector.util.IpAddressMatcher;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.UNKNOWN;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.byType;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_ACCOUNT_UPDATED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_CHARGE_DISPUTE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_CHARGE_REFUND_UPDATED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYOUT_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_BALANCE_AVAILABLE;

@ExtendWith(MockitoExtension.class)
class StripeNotificationServiceTest {
    private static final String FORWARDED_IP_ADDRESSES = "102.108.0.6, 1.2.3.4";
    private static final Set<String> ALLOWED_IP_ADDRESSES = CidrUtils.getIpAddresses(Set.of("1.2.3.0/24", "9.9.9.9/32"));
    private static ObjectMapper objectMapper = new ObjectMapper();

    private StripeNotificationService notificationService;
    private StripeAccountUpdatedHandler stripeAccountUpdatedHandler;

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
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;
    @Mock
    private TaskQueueService mockTaskQueueService;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Captor
    private ArgumentCaptor<Payout> payoutArgumentCaptor;

    private final StripeRefundUpdatedHandler stripeRefundUpdatedHandler = new StripeRefundUpdatedHandler(objectMapper);

    private final String externalId = "external-id";
    private final String sourceId = "source-id";
    private final String webhookTestSigningSecret = "whtest";
    private final String webhookLiveSigningSecret = "whlive";

    @BeforeEach
    void setup() {
        stripeAccountUpdatedHandler = new StripeAccountUpdatedHandler(mockGatewayAccountCredentialsService, objectMapper);
        notificationService = new StripeNotificationService(
                mockCard3dsResponseAuthService,
                mockChargeService,
                stripeGatewayConfig,
                stripeAccountUpdatedHandler,
                stripeRefundUpdatedHandler,
                mockPayoutReconcileQueue,
                mockPayoutEmitterService,
                new IpAddressMatcher(new InetAddressValidator()),
                ALLOWED_IP_ADDRESSES,
                objectMapper,
                mockTaskQueueService);

        lenient().when(stripeGatewayConfig.getWebhookSigningSecrets()).thenReturn(List.of(webhookLiveSigningSecret, webhookTestSigningSecret));
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
    void shouldLogForBalanceAvailableEvent() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_BALANCE_AVAILABLE);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);
        assertTrue(result);
        verify(mockAppender, times(3)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getFormattedMessage(), containsString("Logging stripe balance"));
    }

    @Test
    void shouldLogForDisputeCreatedEvent() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_CHARGE_DISPUTE);
        payload = payload
                .replace("{{type}}", "charge.dispute.created")
                .replace("{{status}}", "needs_response");

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(3)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getFormattedMessage(), containsString("Received a charge.dispute.created event"));
        assertThat(loggingEvent.getArgumentArray().length, is(1));
    }

    @Test
    void shouldLogForDisputeUpdatedEvent() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_CHARGE_DISPUTE);
        payload = payload
                .replace("{{type}}", "charge.dispute.updated")
                .replace("{{status}}", "under_review");

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(3)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getFormattedMessage(), containsString("Received a charge.dispute.updated event"));
        assertThat(loggingEvent.getArgumentArray().length, is(1));
    }

    @Test
    void shouldLogForDisputeClosedEvent() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_CHARGE_DISPUTE);
        payload = payload
                .replace("{{type}}", "charge.dispute.closed")
                .replace("{{status}}", "won");

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(3)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getFormattedMessage(), containsString("Received a charge.dispute.closed event"));
        assertThat(loggingEvent.getArgumentArray().length, is(1));
    }

    @Test
    void shouldLogTheRequirementsAndPayoutsDisabledJson_whenAnAccountUpdatedEventIsReceived() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeAccountUpdatedHandler.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_ACCOUNT_UPDATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getMessage(), containsString("Received an account.updated event for stripe account"));
        assertThat(loggingEvent.getArgumentArray().length, is(4));
    }

    @Test
    void shouldLogWhenChargeRefundEventReceivedWithStatusFailed() {
        Logger root = (Logger) LoggerFactory.getLogger(StripeRefundUpdatedHandler.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_CHARGE_REFUND_UPDATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getMessage(), containsString("Received a charge.refund.updated event with status failed"));
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
        assertThat(payout.getCreatedDate(), is(Instant.parse("2020-03-24T01:30:46Z")));
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
                    Instant.ofEpochSecond(1567622603L), "connect_account_id", payout);

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
    void shouldUpdateCharge_WhenNotificationIsForCapturablePaymentIntent() {
        setUpCharge();
        setUpChargeServiceToReturnCharge();
        when(mockCharge.getAmount()).thenReturn(1000L);
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                sourceId, PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayloadWithTestSecret(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED));
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
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId,
                getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED, "2.0.1"));
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
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT_PAYMENT_FAILED,
                "pi_123", PAYMENT_INTENT_PAYMENT_FAILED);
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), "pi_123")).thenReturn(Optional.of(mockCharge));

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId,
                getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome.DECLINED, "2.0.1"));
    }

    @Test
    void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                sourceId, UNKNOWN);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    void shouldNotUpdateCharge_WhenTransactionIdIsNotAvailable() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                StringUtils.EMPTY, PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);

        final boolean result = notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);

        assertTrue(result);
        verify(mockCard3dsResponseAuthService, never()).process3DSecureAuthorisationWithoutLocking(anyString(), any());
    }

    @Test
    void shouldNotUpdateCharge_WhenChargeIsNotFoundForTransactionId() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                "unknown-source-id", PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);

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
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                sourceId, PAYMENT_INTENT_PAYMENT_FAILED);
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
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                sourceId, PAYMENT_INTENT_PAYMENT_FAILED);
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

    @Test
    void shouldUpdateCredentialsIfNoRequirements() {
        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_ACCOUNT_UPDATED);

        notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);
        verify(mockGatewayAccountCredentialsService, times(1)).activateCredentialIfNotYetActive(anyString());
    }

    @ParameterizedTest
    @MethodSource("requirements")
    void shouldNotUpdateCredentials(String target, String replacement) {
        String payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_ACCOUNT_UPDATED);
        payload = payload.replace(target, replacement);

        notificationService.handleNotificationFor(payload, signPayload(payload), FORWARDED_IP_ADDRESSES);
        verify(mockGatewayAccountCredentialsService, never()).activateCredentialIfNotYetActive(anyString());
    }

    private static String sampleStripeNotification(String location,
                                                   String objectId,
                                                   StripeNotificationType stripeNotificationType) {
        return TestTemplateResourceLoader.load(location)
                .replace("{{id}}", objectId)
                .replace("{{type}}", stripeNotificationType.getType());
    }

    private Auth3dsResult getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome auth3dsResultOutcome) {
        return getAuth3dsResult(auth3dsResultOutcome, "2.0.1");
    }

    private Auth3dsResult getAuth3dsResult(Auth3dsResult.Auth3dsResultOutcome auth3dsResultOutcome, String threeDsVersion) {
        Auth3dsResult auth3dsResult = new Auth3dsResult();
        auth3dsResult.setAuth3dsResult(auth3dsResultOutcome.toString());
        auth3dsResult.setThreeDsVersion(threeDsVersion);
        return auth3dsResult;
    }

    static Stream<Arguments> requirements() {
        return Stream.of(
                arguments("\"payouts_enabled\": true,", "\"payouts_enabled\": false,"),
                arguments("\"charges_enabled\": true,", "\"charges_enabled\": false,"),
                arguments("\"currently_due\": [", "\"currently_due\": [\"individual.dob.day\""),
                arguments("\"past_due\": [", "\"past_due\": [\"individual.dob.day\"")
        );
    }
}
