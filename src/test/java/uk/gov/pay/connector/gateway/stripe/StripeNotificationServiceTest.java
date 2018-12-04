package uk.gov.pay.connector.gateway.stripe;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.UNKNOWN;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_3DS_SOURCE;

@RunWith(MockitoJUnitRunner.class)
public class StripeNotificationServiceTest {
    private StripeNotificationService notificationService;

    @Mock
    private Card3dsResponseAuthService mockCard3dsResponseAuthService;
    @Mock
    private ChargeDao mockChargeDao;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private ChargeEntity mockCharge;
    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;

    private final String externalId = "external-id";
    private final String sourceId = "source-id";
    private final String webhookSigningSecret = "whsec";

    @Before
    public void setup() {
        notificationService = new StripeNotificationService(mockCard3dsResponseAuthService,
                mockChargeService, stripeGatewayConfig);

        when(stripeGatewayConfig.getWebhookSigningSecret()).thenReturn(webhookSigningSecret);
        when(mockCharge.getExternalId()).thenReturn(externalId);
        when(mockCharge.getStatus()).thenReturn(AUTHORISATION_3DS_REQUIRED.getValue());
        when(mockCharge.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockChargeService.findByProviderAndTransactionId(STRIPE.getName(), sourceId)).thenReturn(Optional.of(mockCharge));
    }

    @Test
    public void shouldUpdateCharge_WhenNotificationIsFor3DSSourceChargeable() {
        final String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                sourceId, SOURCE_CHARGEABLE);

        notificationService.handleNotificationFor(payload, signPayload(payload));

        verify(mockCard3dsResponseAuthService).process3DSecureAuthorisationWithoutLocking(externalId, getAuth3dsDetails(Auth3dsDetails.Auth3dsResult.AUTHORISED));
    }

    private String signPayload(String payload) {
        return StripeNotificationUtilTest.generateSigHeader(webhookSigningSecret, payload);
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
                .replace("{{sourceId}}", sourceId)
                .replace("{{type}}", stripeNotificationType.getType());
    }

    private Auth3dsDetails getAuth3dsDetails(Auth3dsDetails.Auth3dsResult auth3dsResult) {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setAuth3dsResult(auth3dsResult.toString());
        return auth3dsDetails;
    }
}
