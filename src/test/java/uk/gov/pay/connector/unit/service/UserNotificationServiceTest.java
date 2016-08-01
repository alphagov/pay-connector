package uk.gov.pay.connector.unit.service;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.service.NotifyClientProvider;
import uk.gov.pay.connector.service.UserNotificationService;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationResponse;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;

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
    NotificationResponse mockNotificationCreatedResponse;
    @Mock
    Notification mockNotification;
    @Mock
    private NotifyConfiguration mockNotifyConfiguration;

    private UserNotificationService userNotificationService;
    private ImmutableMap<String, String> personalisationMap = ImmutableMap.of("key-1", "value-1", "key-2", "value-2");

    @Before
    public void setUp() {
        when(mockConfig.getNotifyConfiguration()).thenReturn(mockNotifyConfiguration);
        when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
    }

    @Test
    public void shouldSendEmail() throws Exception {
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn("100");

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 10, 23, 12, 0, ZoneId.of("UTC")))
                .build();
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        userNotificationService.notifyPaymentSuccessEmail(charge);
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
                map
        );
    }

    @Test
    public void testEmailSendingStatus() throws Exception {

        when(mockNotifyClient.getNotificationById(any())).thenReturn(mockNotification);
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        userNotificationService.checkDeliveryStatus("100");

        verify(mockNotifyClient).getNotificationById("100");
    }

    @Test
    public void testEmailSendingThrowsExceptionForMissingTemplate() throws Exception {
        try {
            reset(mockNotifyConfiguration);
            when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
            userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
            fail("this method should throw an ex");
        } catch(Exception e) {
            assertEquals("config property 'emailTemplateId' is missing or not set, which needs to point to the email template on the notify", e.getMessage());
        }
    }

    @Test
    public void testEmailSendWhenEmailsNotifyDisabled() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(false);
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(), any(), any())).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getNotificationId()).thenReturn("100");

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        userNotificationService.notifyPaymentSuccessEmail(ChargeEntityFixture.aValidChargeEntity().build());
        verifyZeroInteractions(mockNotifyClient);
    }
}
