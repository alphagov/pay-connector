package uk.gov.pay.connector.usernotification.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactoryProvider;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.fail;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserNotificationServiceTest {
    @Mock
    private NotificationClient mockNotifyClient;
    @Mock
    private NotifyClientFactoryProvider mockNotifyClientFactoryProvider;
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
    private UserNotificationService userNotificationService;

    @Before
    public void setUp() {
        when(mockConfig.getNotifyConfiguration()).thenReturn(mockNotifyConfiguration);
        when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(mockNotifyConfiguration.getRefundIssuedEmailTemplateId()).thenReturn("another-template");
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);

        when(mockConfig.getExecutorServiceConfig()).thenReturn(mockExecutorConfiguration);
        when(mockExecutorConfiguration.getThreadsPerCpu()).thenReturn(2);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
    }

    @Test
    public void shouldSendPaymentConfirmationEmailIfEmailNotifyIsEnabled() throws Exception {
        UUID notificationId = randomUUID();
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        HashMap<String, String> personalisation = new HashMap<>();
        personalisation.put("serviceReference", "This is a reference");
        personalisation.put("date", "1 January 2016 - 10:23:12");
        personalisation.put("description", "This is a description");
        personalisation.put("serviceName", "MyService");
        personalisation.put("customParagraph", "^ template body");
        personalisation.put("amount", "5.00");
        personalisation.put("corporateCardSurcharge", "");
        when(mockNotifyClient.sendEmail(mockNotifyConfiguration.getEmailTemplateId(),
                charge.getEmail(),
                personalisation,
                null)).thenReturn(mockNotificationCreatedResponse);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        Optional<String> maybeNotificationId = userNotificationService.sendPaymentConfirmedEmail(charge).get(1000, TimeUnit.SECONDS);
        assertThat(maybeNotificationId.get(), is(notificationId.toString()));
    }

    @Test
    public void shouldSendRefundIssuedEmailIfEmailNotifyIsEnabled() throws Exception {
        UUID notificationId = randomUUID();
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(notificationId);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withCreatedDate(ZonedDateTime.of(2017, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withCharge(charge).build();

        HashMap<String, String> personalisation = new HashMap<>();
        personalisation.put("serviceName", "MyService");
        personalisation.put("serviceReference", "This is a reference");
        personalisation.put("date", "1 January 2016 - 10:23:12");
        personalisation.put("description", "This is a description");
        personalisation.put("amount", "1.00");

        when(mockNotifyClient.sendEmail(mockNotifyConfiguration.getRefundIssuedEmailTemplateId(),
                charge.getEmail(),
                personalisation,
                null)).thenReturn(mockNotificationCreatedResponse);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        Optional<String> maybeNotificationId = userNotificationService.sendRefundIssuedEmail(refundEntity).get(1000, TimeUnit.SECONDS);
        assertThat(maybeNotificationId.get(), is(notificationId.toString()));
    }

    @Test
    public void shouldThrow_ifMissingPaymentConfirmedTemplate() {
        try {
            reset(mockNotifyConfiguration);
            when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
            userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
            fail("this method should throw an ex");
        } catch (Exception e) {
            assertEquals("Check notify config, need to set 'emailTemplateId' (payment confirmation email) and 'refundIssuedEmailTemplateId' properties", e.getMessage());
        }
    }

    @Test
    public void shouldThrow_ifMissingRefundIssuedTemplate() {
        try {
            reset(mockNotifyConfiguration);
            when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
            when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("template");
            userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
            fail("this method should throw an ex");
        } catch (Exception e) {
            assertEquals("Check notify config, need to set 'emailTemplateId' (payment confirmation email) and 'refundIssuedEmailTemplateId' properties", e.getMessage());
        }
    }

    @Test
    public void shouldNotSendPaymentConfirmedEmail_IfNotifyIsDisabled() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(false);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(ChargeEntityFixture.aValidChargeEntity().build());
        idF.get(1000, TimeUnit.SECONDS);

        verifyZeroInteractions(mockNotifyClient);
    }

    @Test
    public void shouldNotSendRefundIssuedEmail_IfNotifyIsDisabled() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(false);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(RefundEntityFixture.aValidRefundEntity().build());
        idF.get(1000, TimeUnit.SECONDS);

        verifyZeroInteractions(mockNotifyClient);
    }

    @Test
    public void shouldNotSendPaymentConfirmedEmail_whenConfirmationEmailNotificationsAreDisabledForService() {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount()
                .getEmailNotifications()
                .get(EmailNotificationType.PAYMENT_CONFIRMED)
                .setEnabled(false);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        userNotificationService.sendPaymentConfirmedEmail(chargeEntity);
        verifyZeroInteractions(mockNotifyClient);
    }

    @Test
    public void shouldRecordNotifyResponseTimesWhenSendPaymentConfirmationEmailSucceeds() throws Exception {
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);
        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verifyNoMoreInteractions(mockCounter);
    }

    @Test
    public void shouldNotSendRefundIssuedEmail_whenConfirmationEmailNotificationsAreDisabledForService() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount()
                .getEmailNotifications()
                .get(EmailNotificationType.REFUND_ISSUED)
                .setEnabled(false);
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withCreatedDate(ZonedDateTime.of(2017, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withCharge(chargeEntity).build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        userNotificationService.sendRefundIssuedEmail(refundEntity);
        verifyZeroInteractions(mockNotifyClient);
    }

    @Test
    public void shouldRecordNotifyResponseTimesWhenSendRefundIssuedEmailSucceeds() throws Exception {
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withCreatedDate(ZonedDateTime.of(2017, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withCharge(charge).build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity);
        idF.get(1000, TimeUnit.SECONDS);
        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verifyNoMoreInteractions(mockCounter);
    }

    @Test
    public void shouldRecordNotifyResponseTimesAndFailureWhenSendPaymentConfirmationEmailFails() throws Exception {
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenThrow(NotificationClientException.class);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);
        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verify(mockCounter).inc();
    }

    @Test
    public void shouldRecordNotifyResponseTimesAndFailureWhenSendRefundIssuedEmailFails() throws Exception {
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenThrow(NotificationClientException.class);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withCreatedDate(ZonedDateTime.of(2017, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withCharge(charge).build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity);
        idF.get(1000, TimeUnit.SECONDS);
        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verify(mockCounter).inc();
    }

    @Test
    public void shouldSendBlankCustomParagraphIfNotSetInConfirmationEmail() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        GatewayAccountEntity accountEntity = charge.getGatewayAccount();
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity);
        emailNotificationEntity.setTemplateBody(null);
        accountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, emailNotificationEntity);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge);
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
                charge.getEmail(),
                map, null
        );
    }

    @Test
    public void shouldSendCorporateCardSurchargeWithMessage_whenSurchargePresent() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withCorporateSurcharge(250L)
                .build();
        GatewayAccountEntity accountEntity = charge.getGatewayAccount();
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity);
        emailNotificationEntity.setTemplateBody(null);
        accountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, emailNotificationEntity);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);

        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", "This is a reference");
        map.put("date", "1 January 2016 - 10:23:12");
        map.put("description", "This is a description");
        map.put("serviceName", "MyService");
        map.put("customParagraph", "");
        map.put("amount", "5.00");
        map.put("corporateCardSurcharge", "Your payment includes a fee of £2.50 for using a corporate credit or debit card.");

        verify(mockNotifyClient).sendEmail(
                mockNotifyConfiguration.getEmailTemplateId(),
                charge.getEmail(),
                map, null
        );
    }

    @Test
    public void shouldUse_customNonGovUkBrandedEmail_whenAccountConfiguredToCustomBrandingForConfirmationEmail() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance(any())).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key", "template_id", "my-template-id"))
                .build();

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance("my-api-key");
        verify(mockNotifyClient).sendEmail(eq("my-template-id"), anyString(), any(Map.class), any());
    }

    @Test
    public void shouldUse_customNonGovUkBrandedEmail_whenAccountConfiguredToCustomBrandingForRefundIssuedEmail() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance(any())).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key", "refund_issued_template_id", "template_id2"))
                .build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(100L)
                .withCreatedDate(ZonedDateTime.of(2017, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withCharge(charge).build();
        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refundEntity);
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance("my-api-key");
        verify(mockNotifyClient).sendEmail(eq("template_id2"), anyString(), any(Map.class), any());
    }

    @Test
    public void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveApiToken_ForPaymentConfirmationEmail() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withNotifySettings(ImmutableMap.of("template_id", "my-template-id"))
                .build();

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("some-template"), anyString(), any(Map.class), any());
    }

    @Test
    public void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveTemplate_ForPaymentConfirmationEmail() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key"))
                .build();

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendPaymentConfirmedEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("some-template"), anyString(), any(Map.class), any());
    }

    @Test
    public void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveApiToken_ForRefundIssuedEmail() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);


        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withNotifySettings(ImmutableMap.of("refund_issued_template_id", "my-template-id"))
                .build();

        RefundEntity refund = RefundEntityFixture.aValidRefundEntity().withCharge(charge).build();


        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refund);
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("another-template"), anyString(), any(Map.class), any());
    }

    @Test
    public void shouldUse_GovUkBrandedEmails_whenNotifySettingsDoNotHaveTemplate_ForRefundIssuedEmail() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientFactoryProvider.clientFactory()).thenReturn(mockNotifyClientFactory);
        when(mockNotifyClientFactory.getInstance()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .withNotifySettings(ImmutableMap.of("api_token", "my-api-key"))
                .build();

        RefundEntity refund = RefundEntityFixture.aValidRefundEntity().withCharge(charge).build();

        userNotificationService = new UserNotificationService(mockNotifyClientFactoryProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.sendRefundIssuedEmail(refund);
        idF.get(1000, TimeUnit.SECONDS);

        verify(mockNotifyClientFactory).getInstance();
        verify(mockNotifyClient).sendEmail(eq("another-template"), anyString(), any(Map.class), any());
    }
}
