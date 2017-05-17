package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UserNotificationServiceTest {
    @Mock
    private NotificationClient mockNotifyClient;
    @Mock
    private NotifyClientProvider mockNotifyClientProvider;
    @Mock
    private ConnectorConfiguration mockConfig;
    @Mock
    SendEmailResponse mockNotificationCreatedResponse;
    @Mock
    Notification mockNotification;
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
    private ImmutableMap<String, String> personalisationMap = ImmutableMap.of("key-1", "value-1", "key-2", "value-2");

    @Before
    public void setUp() {
        when(mockConfig.getNotifyConfiguration()).thenReturn(mockNotifyConfiguration);
        when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);

        when(mockConfig.getExecutorServiceConfig()).thenReturn(mockExecutorConfiguration);
        when(mockExecutorConfiguration.getThreadsPerCpu()).thenReturn(2);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);
    }

    @Test
    public void shouldSendEmailIfEmailNotifyIsEnabled() throws Exception {
        when(mockConfig.getNotifyConfiguration().isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.notifyPaymentSuccessEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);

        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", "This is a reference");
        map.put("date", "1 January 2016 - 10:23:12");
        map.put("description", "This is a description");
        map.put("serviceName", "MyService");
        map.put("customParagraph", "template body");
        map.put("amount", "5.00");

        verify(mockNotifyClient).sendEmail(
                mockNotifyConfiguration.getEmailTemplateId(),
                charge.getEmail(),
                map, null
        );
    }

    @Test
    public void testEmailSendingStatus() throws Exception {

        when(mockNotifyClient.getNotificationById(any())).thenReturn(mockNotification);
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);
        userNotificationService.checkDeliveryStatus("100");

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 1, 1, 0, 0, ZoneId.of("UTC")))
                .build();
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);
        userNotificationService.notifyPaymentSuccessEmail(charge);

        verify(mockNotifyClient).getNotificationById("100");
    }

    @Test
    public void testEmailSendingThrowsExceptionForMissingTemplate() throws Exception {
        try {
            reset(mockNotifyConfiguration);
            when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
            userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);
            fail("this method should throw an ex");
        } catch (Exception e) {
            assertEquals("config property 'emailTemplateId' is missing or not set, which needs to point to the email template on the notify", e.getMessage());
        }
    }

    @Test
    public void testEmailSendWhenEmailsNotifyDisabled() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(false);
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);
        Future<Optional<String>> idF = userNotificationService.notifyPaymentSuccessEmail(ChargeEntityFixture.aValidChargeEntity().build());
        idF.get(1000, TimeUnit.SECONDS);

        verifyZeroInteractions(mockNotifyClient);
    }

    @Test
    public void whenEmailNotificationsAreDisabledForService_emailShouldNotBeSent() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.getGatewayAccount().getEmailNotification().setEnabled(false);

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);
        userNotificationService.notifyPaymentSuccessEmail(chargeEntity);
        verifyZeroInteractions(mockNotifyClient);
    }

    @Test
    public void shouldRecordNotifyResponseTimesWhenSendEmailSucceeds() throws Exception {
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.notifyPaymentSuccessEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);
        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verifyNoMoreInteractions(mockCounter);
    }

    @Test
    public void shouldRecordNotifyResponseTimesAndFailureWhenSendEmailFails() throws Exception {
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any(), any())).thenThrow(NotificationClientException.class);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(randomUUID());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig, mockEnvironment);

        Future<Optional<String>> idF = userNotificationService.notifyPaymentSuccessEmail(charge);
        idF.get(1000, TimeUnit.SECONDS);
        verify(mockMetricRegistry).histogram("notify-operations.response_time");
        verify(mockHistogram).update(anyLong());
        verify(mockCounter).inc();
    }
}
