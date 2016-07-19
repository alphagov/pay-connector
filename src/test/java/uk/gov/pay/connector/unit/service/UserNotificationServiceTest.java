package uk.gov.pay.connector.unit.service;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.notifications.client.api.GovNotifyApiClient;
import uk.gov.notifications.client.model.EmailRequest;
import uk.gov.notifications.client.model.NotificationCreatedResponse;
import uk.gov.notifications.client.model.StatusResponse;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.NotifyClientProvider;
import uk.gov.pay.connector.service.UserNotificationService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
        when(mockNotifyClient.sendEmail(any(EmailRequest.class))).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getId()).thenReturn("100");

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        userNotificationService.notifyPaymentSuccessEmail(ChargeEntityFixture.aValidChargeEntity().build());
        verify(mockNotifyClient).sendEmail(any(EmailRequest.class));
    }

    @Test
    public void testEmailSendingStatus() throws Exception {
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        userNotificationService.checkDeliveryStatus("100");

        verify(mockNotifyClient).checkStatus("100");
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
    public void testEmailRequestBuilderWithPersonalisation() {
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        EmailRequest emailRequest = userNotificationService.buildRequest("test@email.com", "1234", personalisationMap);
        assertEquals(personalisationMap, emailRequest.getPersonalisation().asMap());
    }

    @Test
    public void testEmailRequestBuilderWithoutPersonalisation() {
        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        EmailRequest emailRequest = userNotificationService.buildRequest("test@email.com", "1234", Collections.EMPTY_MAP);
        assertNull(emailRequest.getPersonalisation());
    }


    @Test
    public void shouldSendEmailWithPersonalisation() throws Exception {
        long chargeId = 123456L;
        long amount = 1000L;
        String description = "Description";
        String reference = "Reference";

        ChargeEntity charge = ChargeEntityFixture
                .aValidChargeEntity()
                .withId(chargeId)
                .withStatus(ChargeStatus.CAPTURED)
                .withAmount(amount)
                .withDescription(description)
                .withReference(reference)
                .withCreatedDate(ZonedDateTime.of(2016, 1, 1, 1, 1, 0, 0, ZoneId.of("UTC")))
                .build();

        charge.getGatewayAccount().setServiceName("MyService");

        Map<String, String> expectedParameters = new ImmutableMap.Builder<String, String>()
                .put("serviceReference", reference)
                .put("date", "1 January 2016")
                .put("amount", "10.00")
                .put("description", charge.getDescription())
                .put("customParagraph", "template body")
                .put("serviceName", charge.getGatewayAccount().getServiceName())
                .build();

        when(mockNotifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(EmailRequest.class))).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getId()).thenReturn("100");

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);

        assertEquals("100", userNotificationService.notifyPaymentSuccessEmail(charge).get());

        verify(mockNotifyClient).sendEmail(argThat(new IsEmailRequestWithParameters(expectedParameters)));
    }

    @Test
    public void testEmailSendWhenEmailsNotifyDisabled() throws Exception {
        when(mockNotifyConfiguration.isEmailNotifyEnabled()).thenReturn(false);
        when(mockNotifyClientProvider.get()).thenReturn(mockNotifyClient);
        when(mockNotifyClient.sendEmail(any(EmailRequest.class))).thenReturn(mockNotificationCreatedResponse);
        when(mockNotificationCreatedResponse.getId()).thenReturn("100");

        userNotificationService = new UserNotificationService(mockNotifyClientProvider, mockConfig);
        userNotificationService.notifyPaymentSuccessEmail(ChargeEntityFixture.aValidChargeEntity().build());
        verifyZeroInteractions(mockNotifyClient);
    }

    class IsEmailRequestWithParameters extends ArgumentMatcher<EmailRequest> {
        private final Map<String, String> expectedParameters;

        public IsEmailRequestWithParameters(Map<String, String> expectedParameters) {
            this.expectedParameters = expectedParameters;
        }

        public boolean matches(Object actualEmailRequest) {
            return ((EmailRequest) actualEmailRequest)
                    .getPersonalisation()
                    .asMap()
                    .equals(expectedParameters);
        }
    }
}
