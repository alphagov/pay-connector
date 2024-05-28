package uk.gov.pay.connector.charge.resource;

import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;

import java.util.UUID;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.PAYMENT_CONFIRMED;

public class ChargesApiResourceResendConfirmationEmailIT {
    private static final NotifyClientFactory notifyClientFactory = mock(NotifyClientFactory.class);
    private static final NotificationClient notificationClient = mock(NotificationClient.class);
    
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            ChargesApiResourceResendConfirmationEmailIT.ConnectorAppWithCustomInjector.class,
            config("notifyConfig.emailNotifyEnabled", "true")
    );
    
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());
    private final String emailAddress = "a@b.com";

    @BeforeAll
    static void before() {
        when(notifyClientFactory.getInstance()).thenReturn(notificationClient);
    }

    @BeforeEach
    void setup() {
        app.getDatabaseTestHelper().addEmailNotification(Long.valueOf(testBaseExtension.getAccountId()), "a template", true, PAYMENT_CONFIRMED);
    }   

    @Test
    void shouldReturn204WhenEmailSuccessfullySent() throws Exception {
        String transactionId = String.valueOf(RandomUtils.nextInt());

        ChargeUtils.ExternalChargeId chargeId = createNewCharge(transactionId, testBaseExtension.getPaymentProvider());

        var notificationId = UUID.randomUUID();
        var mockResponse = mock(SendEmailResponse.class);
        when(mockResponse.getNotificationId()).thenReturn(notificationId);
        when(notificationClient.sendEmail(any(), eq(emailAddress), anyMap(), eq(null), any())).thenReturn(mockResponse);

        given().port(app.getLocalPort())
                .body("")
                .contentType(APPLICATION_JSON)
                .post(String.format("/v1/api/accounts/%s/charges/%s/resend-confirmation-email", testBaseExtension.getAccountId(), chargeId))
                .then()
                .statusCode(204);
    }
    
    private ChargeUtils.ExternalChargeId createNewCharge(String transactionId, String paymentProvider) {
        return ChargeUtils.createNewChargeWithAccountId(
                ChargeStatus.CREATED,
                transactionId,
                testBaseExtension.getAccountId(),
                app.getDatabaseTestHelper(),
                emailAddress,
                paymentProvider);
    }
    
    public static class ConnectorAppWithCustomInjector extends ConnectorApp {

        @Override
        protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
            return new ConnectorModuleWithOverrides(configuration, environment);
        }
    }

    private static class ConnectorModuleWithOverrides extends ConnectorModule {

        public ConnectorModuleWithOverrides(ConnectorConfiguration configuration, Environment environment) {
            super(configuration, environment);
        }

        @Override
        protected NotifyClientFactory getNotifyClientFactory(ConnectorConfiguration connectorConfiguration) {
            return notifyClientFactory;
        }
    }
}
