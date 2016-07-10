package uk.gov.pay.connector.unit.service;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.notifications.client.api.GovNotifyApiClient;
import uk.gov.notifications.client.model.EmailRequest;
import uk.gov.notifications.client.model.NotificationCreatedResponse;
import uk.gov.notifications.client.model.Personalisation;
import uk.gov.notifications.client.model.StatusResponse;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.NotifyClientProvider;
import uk.gov.pay.connector.service.UserNotificationService;

import java.util.Collections;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UserNotificationServiceTest {
    @Mock
    private GovNotifyApiClient mockNotifyClient;
    @Mock
    private NotifyClientProvider mockNotifyClientProvider;
    @Mock
    private NotificationCreatedResponse mockNotificationCreatedResponse;
    @Mock
    private StatusResponse mockStatusResponse;
    @Mock
    private ConnectorConfiguration mockConfig;
    @Mock
    private NotifyConfiguration mockNotifyConfiguration;

    private UserNotificationService userNotificationService;
    private ImmutableMap<String, Object> personalisationMap = ImmutableMap.of("key-1", "value-1", "key-2", "value-2");

    @Before
    public void setUp() {
        when(mockConfig.getNotifyConfiguration()).thenReturn(mockNotifyConfiguration);
        when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
    }

    @Test
    public void shouldSendEmail() throws Exception {
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(EmailRequest.class))).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getId()).thenReturn("100");

        userNotificationService.notifyPaymentSuccessEmail("test@email.com");
        verify(mockNotifyClient).sendEmail(any(EmailRequest.class));
    }

    @Test
    public void testEmailSendingStatus() throws Exception {
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.checkStatus("100")).thenReturn(mockStatusResponse);

        userNotificationService.checkDeliveryStatus("100");
        verify(mockStatusResponse).getStatus();
    }

    @Test
    public void testEmailSendingThrowsExceptionForMissingConfigParam() throws Exception {
        try {
            reset(mockNotifyConfiguration);
            userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
            fail("this method should throw an ex");
        } catch(Exception e) {
            assertEquals("config property 'emailTemplateId' is missing or not set, which needs to point to the email template on the notify", e.getMessage());
        }
    }
    @Test
    public void testEmailRequestBuilderWithPersonalisation() {
        EmailRequest emailRequest = userNotificationService.buildRequest("test@email.com", "1234", personalisationMap);
        assertEquals(personalisationMap, emailRequest.getPersonalisation().asMap());
    }

    @Test
    public void testEmailRequestBuilderWithoutPersonalisation() {
        EmailRequest emailRequest = userNotificationService.buildRequest("test@email.com", "1234", Collections.EMPTY_MAP);
        assertNull(emailRequest.getPersonalisation());
    }
}
