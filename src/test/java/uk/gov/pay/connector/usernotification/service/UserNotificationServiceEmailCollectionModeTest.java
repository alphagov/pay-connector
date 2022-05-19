package uk.gov.pay.connector.usernotification.service;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;

@RunWith(JUnitParamsRunner.class)
public class UserNotificationServiceEmailCollectionModeTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ConnectorConfiguration connectorConfig;
    
    @Mock
    private Environment environment;

    @Mock
    private NotifyClientFactory notifyClientFactory;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private SendEmailResponse mockNotificationCreatedResponse;

    @Mock
    private MetricRegistry metricRegistry;

    @Mock
    private ExecutorServiceConfig mockExecutorConfiguration;

    @Mock
    private NotifyConfiguration notifyConfiguration;
    
    private UserNotificationService userNotificationService;
    
    @Before
    public void setUp() {
        when(connectorConfig.getNotifyConfiguration()).thenReturn(notifyConfiguration);
        when(notifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(notifyConfiguration.getRefundIssuedEmailTemplateId()).thenReturn("another-template");
        when(notifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);

        when(connectorConfig.getExecutorServiceConfig()).thenReturn(mockExecutorConfiguration);
        when(mockExecutorConfiguration.getThreadsPerCpu()).thenReturn(2);

        when(environment.metrics()).thenReturn(metricRegistry);

        userNotificationService = new UserNotificationService(notifyClientFactory, connectorConfig, environment);

    }
    
    @Test
    @Parameters({
            "OPTIONAL, email@example.com, true",
            "OPTIONAL, null, false",
            "OFF, null, false",
            "OFF, email@example.com, false",
    })
    public void determineSendingEmailForEmailCollectionModes(String emailCollectionMode, 
                                                          @Nullable String emailAddress, 
                                                          boolean shouldEmailBeSent) throws Exception {

        if ("OPTIONAL".equals(emailCollectionMode) && "email@example.com".equals(emailAddress) && shouldEmailBeSent) {
            when(notifyClientFactory.getInstance()).thenReturn(notificationClient);
            when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), isNull(), isNull())).thenReturn(mockNotificationCreatedResponse);
            when(metricRegistry.histogram(anyString())).thenReturn(mock(Histogram.class));
            when(notifyClientFactory.getInstance()).thenReturn(notificationClient);
            when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(UUID.randomUUID());
            when(mockNotificationCreatedResponse.getNotificationId()).thenReturn(UUID.randomUUID());
        }

        var gatewayAccount = defaultGatewayAccountEntity();
        gatewayAccount.setEmailCollectionMode(EmailCollectionMode.fromString(emailCollectionMode));
        var chargeEntity = aValidChargeEntity().withEmail(emailAddress).withGatewayAccountEntity(gatewayAccount).build();
        userNotificationService.sendPaymentConfirmedEmail(chargeEntity, chargeEntity.getGatewayAccount()).get(1000, TimeUnit.SECONDS);

        verify(notificationClient, times(shouldEmailBeSent? 1 : 0)).sendEmail(anyString(), anyString(), anyMap(), isNull(), isNull());
    }
}
