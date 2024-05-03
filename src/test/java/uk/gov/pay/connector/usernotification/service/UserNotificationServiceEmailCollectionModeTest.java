package uk.gov.pay.connector.usernotification.service;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Clock;
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

@ExtendWith(MockitoExtension.class)
class UserNotificationServiceEmailCollectionModeTest {

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
    @Mock
    private TaskQueueService mockTaskQueueService;
    
    private UserNotificationService userNotificationService;
    
    @BeforeEach
    void setUp() {
        when(connectorConfig.getNotifyConfiguration()).thenReturn(notifyConfiguration);
        when(notifyConfiguration.getEmailTemplateId()).thenReturn("some-template");
        when(notifyConfiguration.getRefundIssuedEmailTemplateId()).thenReturn("another-template");
        when(notifyConfiguration.isEmailNotifyEnabled()).thenReturn(true);

        when(connectorConfig.getExecutorServiceConfig()).thenReturn(mockExecutorConfiguration);
        when(mockExecutorConfiguration.getThreadsPerCpu()).thenReturn(2);

        when(environment.metrics()).thenReturn(metricRegistry);

        userNotificationService = new UserNotificationService(notifyClientFactory, connectorConfig, environment, mockTaskQueueService, Clock.systemUTC());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "OPTIONAL, email@example.com, true",
            "OPTIONAL, null, false",
            "OFF, null, false",
            "OFF, email@example.com, false",
    }, nullValues={"null"})
    void determineSendingEmailForEmailCollectionModes(String emailCollectionMode, String emailAddress,
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
