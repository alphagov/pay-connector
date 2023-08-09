package uk.gov.pay.connector.usernotification.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserNotificationServiceTest {
    @Mock
    private NotificationClient mockNotifyClient;
    @Mock
    private NotifyClientFactory mockNotifyClientFactory;
    @Mock
    private ConnectorConfiguration mockConfig;
    @Mock
    private SendEmailResponse mockNotificationCreatedResponse;
    @Mock
    private NotifyConfiguration mockNotifyConfiguration;
    @Mock
    private ExecutorServiceConfig mockExecutorConfiguration;
    @Mock
    private MetricRegistry mockMetricRegistry;
    @Mock
    private Environment mockEnvironment;
    @Mock
    private Histogram mockHistogram;
    @Mock
    private Counter mockCounter;

    private final UUID notificationId = randomUUID();

    private final ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
            .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
            .build();

    private final Charge charge = Charge.from(chargeEntity);

    private final GatewayAccountEntity gatewayAccountEntity = chargeEntity.getGatewayAccount();

    private final RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
            .withAmount(100L)
            .withCreatedDate(ZonedDateTime.of(2017, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
            .build();

    private UserNotificationService userNotificationService;

    @BeforeEach
    public void setUp() {
        when(mockConfig.getNotifyConfiguration()).thenReturn(mockNotifyConfiguration);
        when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(mockNotifyConfiguration.getRefundIssuedEmailTemplateId()).thenReturn("another-template");
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);

        when(mockConfig.getExecutorServiceConfig()).thenReturn(mockExecutorConfiguration);
        when(mockExecutorConfiguration.getThreadsPerCpu()).thenReturn(2);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);
    }

    @Test
    void shouldSendPaymentConfirmationEmailIfEmailNotifyIsEnabled() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        HashMap<String, String> personalisation = new HashMap<>();
        personalisation.put("serviceReference", "This is a reference");
        personalisation.put("date", "1 January 2016 - 10:23:12");
        personalisation.put("description", "This is a description");
        personalisation.put("serviceName", "MyService");
        personalisation.put("customParagraph", "^ template body");
        personalisation.put("amount", "5.00");
        personalisation.put("corporateCardSurcharge", "");
        when(mockNotifyClient.sendEmail(mockNotifyConfiguration.getEmailTemplateId(),
                chargeEntity.getEmail(),
                personalisation,
                null,
                null)).thenReturn(mockNotificationCreatedResponse);

        Optional<String> maybeNotificationId = userNotificationService.sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount()).get(1000, TimeUnit.SECONDS);
        assertThat(maybeNotificationId.get(), is(notificationId.toString()));
    }

    @Test
    void shouldSendPaymentConfirmationEmailWithReferenceInCustomParagraphIfEmailNotifyIsEnabled() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccountEntity.getEmailNotifications().get(EmailNotificationType.PAYMENT_CONFIRMED)
                .setTemplateBody("Here’s the ref: $reference. Here it is again: abc$referencedef. And to end: $reference");

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withReference(ServicePaymentReference.of("123$000"))
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .build();

        Map<String, String> personalisation = new HashMap<>();
        personalisation.put("serviceReference", "123$000");
        personalisation.put("date", "1 January 2016 - 10:23:12");
        personalisation.put("description", "This is a description");
        personalisation.put("serviceName", "MyService");
        personalisation.put("customParagraph", "^ Here’s the ref: 123$000. Here it is again: abc123$000def. And to end: 123$000");
        personalisation.put("amount", "5.00");
        personalisation.put("corporateCardSurcharge", "");

        when(mockNotifyClient.sendEmail(mockNotifyConfiguration.getEmailTemplateId(),
                charge.getEmail(),
                personalisation,
                null,
                null)).thenReturn(mockNotificationCreatedResponse);

        Optional<String> maybeNotificationId = userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount()).get(1000, TimeUnit.SECONDS);
        assertThat(maybeNotificationId.get(), is(notificationId.toString()));
    }

    @Test
    void shouldSendPaymentConfirmationEmailSynchronouslyIfEmailNotifyIsEnabled() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        HashMap<String, String> personalisation = new HashMap<>();
        personalisation.put("serviceReference", "This is a reference");
        personalisation.put("date", "1 January 2016 - 10:23:12");
        personalisation.put("description", "This is a description");
        personalisation.put("serviceName", "MyService");
        personalisation.put("customParagraph", "^ template body");
        personalisation.put("amount", "5.00");
        personalisation.put("corporateCardSurcharge", "");
        when(mockNotifyClient.sendEmail(mockNotifyConfiguration.getEmailTemplateId(),
                chargeEntity.getEmail(),
                personalisation,
                null,
                null)).thenReturn(mockNotificationCreatedResponse);

        Optional<String> maybeNotificationId = userNotificationService.sendPaymentConfirmedEmailSynchronously(charge, chargeEntity.getGatewayAccount());
        assertThat(maybeNotificationId.get(), is(notificationId.toString()));
    }

    @Test
    void shouldSendRefundIssuedEmailIfEmailNotifyIsEnabled() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        HashMap<String, String> personalisation = new HashMap<>();
        personalisation.put("serviceName", "MyService");
        personalisation.put("serviceReference", "This is a reference");
        personalisation.put("date", "1 January 2016 - 10:23:12");
        personalisation.put("description", "This is a description");
        personalisation.put("amount", "1.00");

        when(mockNotifyClient.sendEmail(mockNotifyConfiguration.getRefundIssuedEmailTemplateId(),
                chargeEntity.getEmail(),
                personalisation,
                null,
                null)).thenReturn(mockNotificationCreatedResponse);

        Optional<String> maybeNotificationId = userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity).get(1000, TimeUnit.SECONDS);
        assertThat(maybeNotificationId.get(), is(notificationId.toString()));
    }

    @Test
    void shouldThrow_ifMissingPaymentConfirmedTemplate() {
        try {
            reset(mockNotifyConfiguration);
            when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
            userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);
            fail("this method should throw an ex");
        } catch (Exception e) {
            assertEquals("Check notify config, need to set 'emailTemplateId' (payment confirmation email) and 'refundIssuedEmailTemplateId' properties", e.getMessage());
        }
    }

    @Test
    void shouldThrow_ifMissingRefundIssuedTemplate() {
        try {
            reset(mockNotifyConfiguration);
            when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
            when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("template");
            userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);
            fail("this method should throw an ex");
        } catch (Exception e) {
            assertEquals("Check notify config, need to set 'emailTemplateId' (payment confirmation email) and 'refundIssuedEmailTemplateId' properties", e.getMessage());
        }
    }

    @Test
    void shouldNotSendPaymentConfirmedEmail_IfNotifyIsDisabled() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(false);
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verifyNoInteractions(mockNotifyClient);
    }

    @Test
    void shouldNotSendRefundIssuedEmail_IfNotifyIsDisabled() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(false);
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
        idF.get(1000, TimeUnit.SECONDS);

        verifyNoInteractions(mockNotifyClient);
    }

    @Test
    void shouldNotSendEmail_IfEmailEnabledButChargeDoesNotHaveAnEmailAddress() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
        Charge chargeWithoutEmail = Charge.from(ChargeEntityFixture.aValidChargeEntity().withEmail(null).build());
        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity, chargeWithoutEmail, gatewayAccountEntity);
        idF.get(1000, TimeUnit.SECONDS);

        verifyNoInteractions(mockNotifyClient);
    }

    @Test
    void shouldNotSendPaymentConfirmedEmail_whenConfirmationEmailNotificationsAreDisabledForService() {
        chargeEntity.getGatewayAccount()
                .getEmailNotifications()
                .get(EmailNotificationType.PAYMENT_CONFIRMED)
                .setEnabled(false);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);
        userNotificationService.sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount());
        verifyNoInteractions(mockNotifyClient);
    }

    @Test
    void shouldRecordNotifyResponseTimesWhenSendPaymentConfirmationEmailSucceeds() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verifyNoMoreInteractions(mockCounter);
    }

    @Test
    void shouldNotSendRefundIssuedEmail_whenConfirmationEmailNotificationsAreDisabledForService() {
        chargeEntity.getGatewayAccount()
                .getEmailNotifications()
                .get(EmailNotificationType.REFUND_ISSUED)
                .setEnabled(false);

        userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
        verifyNoInteractions(mockNotifyClient);
    }

    @Test
    void shouldRecordNotifyResponseTimesWhenSendRefundIssuedEmailSucceeds() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
 
        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
        idF.get(1000, TimeUnit.SECONDS);
        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verifyNoMoreInteractions(mockCounter);
    }

    @Test
    void shouldRecordNotifyResponseTimesAndFailureWhenSendPaymentConfirmationEmailFails() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenThrow(NotificationClientException.class);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verify(mockCounter).inc();
    }

    @Test
    void shouldRecordNotifyResponseTimesAndFailureWhenSendRefundIssuedEmailFails() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockConfig.getExecutorServiceConfig()).thenReturn(mockExecutorConfiguration);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenThrow(NotificationClientException.class);
 
        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verify(mockCounter).inc();
    }

    @Test
    void shouldSendBlankCustomParagraphIfNotSetInConfirmationEmail() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        GatewayAccountEntity accountEntity = chargeEntity.getGatewayAccount();
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity);
        emailNotificationEntity.setTemplateBody(null);
        accountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, emailNotificationEntity);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", "This is a reference");
        map.put("date", "1 January 2016 - 10:23:12");
        map.put("description", "This is a description");
        map.put("serviceName", "MyService");
        map.put("customParagraph", "");
        map.put("amount", "5.00");
        map.put("corporateCardSurcharge", "");

        verify(mockNotifyClient).sendEmail(
                mockNotifyConfiguration.getEmailTemplateId(),
                chargeEntity.getEmail(),
                map, null, null
        );
    }

    @Test
    void shouldSendCorporateCardSurchargeWithMessage_whenSurchargePresent() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withCorporateSurcharge(250L)
                .build();

        GatewayAccountEntity accountEntity = charge.getGatewayAccount();
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity);
        emailNotificationEntity.setTemplateBody(null);
        accountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, emailNotificationEntity);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", "This is a reference");
        map.put("date", "1 January 2016 - 10:23:12");
        map.put("description", "This is a description");
        map.put("serviceName", "MyService");
        map.put("customParagraph", "");
        map.put("amount", "7.50");
        map.put("corporateCardSurcharge", "Your payment includes a fee of £2.50 for using a corporate credit or debit card.");

        verify(mockNotifyClient).sendEmail(
                mockNotifyConfiguration.getEmailTemplateId(),
                charge.getEmail(),
                map, null, null
        );
    }

    @Test
    void shouldUse_customNonGovUkBrandedEmail_whenAccountConfiguredToCustomBrandingForConfirmationEmail() throws Exception {
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClientFactory.getInstance("my-api-key")).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        String emailReplyToId = "a-reply-to-id";
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key", "template_id", "my-template-id", "email_reply_to_id", emailReplyToId))
                .build();

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance("my-api-key");
        verify(mockNotifyClient).sendEmail(eq("my-template-id"), anyString(), anyMap(), any(), eq(emailReplyToId));
    }

    @Test
    void shouldUse_customNonGovUkBrandedEmail_whenAccountConfiguredToCustomBrandingForConfirmationEmail_whenNoEmailReplyToIdSet() throws Exception {
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClientFactory.getInstance("my-api-key")).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key", "template_id", "my-template-id"))
                .build();

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance("my-api-key");
        verify(mockNotifyClient).sendEmail(eq("my-template-id"), anyString(), anyMap(), any(), isNull());
    }

    @Test
    void shouldUse_customNonGovUkBrandedEmail_whenAccountConfiguredToCustomBrandingForRefundIssuedEmail() throws Exception {
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClientFactory.getInstance("my-api-key")).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        String emailReplyToId = "a-reply-to-id";
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key", "refund_issued_template_id", "template_id2", "email_reply_to_id", emailReplyToId))
                .build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withCreatedDate(ZonedDateTime.of(2017, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();

        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity, Charge.from(chargeEntity), chargeEntity.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance("my-api-key");
        verify(mockNotifyClient).sendEmail(eq("template_id2"), anyString(), anyMap(), isNull(), eq(emailReplyToId));
    }

    @Test
    void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveApiToken_ForPaymentConfirmationEmail() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withNotifySettings(ImmutableMap.of("template_id", "my-template-id"))
                .build();
 
        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("some-template"), anyString(), anyMap(), any(), isNull());
    }

    @Test
    void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveTemplate_ForPaymentConfirmationEmail() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key"))
                .build();
 
        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("some-template"), anyString(), anyMap(), any(), isNull());
    }

    @Test
    void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveApiToken_ForRefundIssuedEmail() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);


        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
 
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withNotifySettings(ImmutableMap.of("refund_issued_template_id", "my-template-id"))
                .build();

        RefundEntity refund = RefundEntityFixture.aValidRefundEntity().build();

        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refund, Charge.from(chargeEntity), chargeEntity.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("another-template"), anyString(), anyMap(), any(), isNull());
    }

    @Test
    void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveTemplate_ForRefundIssuedEmail() throws Exception {
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactory, mockConfig, mockEnvironment);

        when(mockNotifyClient.sendEmail(any(), any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.parse("2016-01-01T10:23:12Z"))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key"))
                .build();

        RefundEntity refund = RefundEntityFixture.aValidRefundEntity().build();
 
        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refund, Charge.from(chargeEntity), chargeEntity.getGatewayAccount());
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("another-template"), anyString(), anyMap(), any(), isNull());
    }
}
