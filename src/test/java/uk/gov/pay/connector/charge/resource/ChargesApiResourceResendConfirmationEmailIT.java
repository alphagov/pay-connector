package uk.gov.pay.connector.charge.resource;

import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.stream.Stream;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
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
    private static final AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            ChargesApiResourceResendConfirmationEmailIT.ConnectorAppWithCustomInjector.class,
            config("notifyConfig.emailNotifyEnabled", "true")
    );

    @RegisterExtension
    private static final ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    private static final String EMAIL_ADDRESS = "a@b.com";
    
    private DatabaseFixtures.TestAccount testAccount;
    private ChargeUtils.ExternalChargeId chargeId;

    @BeforeAll
    static void before() {
        when(notifyClientFactory.getInstance()).thenReturn(notificationClient);
    }

    @BeforeEach
    void setup() throws NoSuchAlgorithmException, NotificationClientException {
        SecureRandom secureRandom = SecureRandom.getInstanceStrong();
        testAccount = testBaseExtension.getTestAccount();
        chargeId = createNewCharge(String.valueOf(secureRandom.nextInt()), testBaseExtension.getPaymentProvider());
        app.getDatabaseTestHelper().addEmailNotification(Long.valueOf(testBaseExtension.getAccountId()), "a template", true, PAYMENT_CONFIRMED);
        SendEmailResponse mockResponse = mock(SendEmailResponse.class);
        when(mockResponse.getNotificationId()).thenReturn(UUID.randomUUID());
        when(notificationClient.sendEmail(any(), eq(EMAIL_ADDRESS), anyMap(), eq(null), any())).thenReturn(mockResponse);
    }

    @Nested
    @DisplayName("Given an account id")
    class ByAccountId {

        @Nested
        @DisplayName("Then resend confirmation email")
        class ResendConfirmationEmail {

            @Test
            @DisplayName("Should return 204 when successful")
            void shouldReturn204WhenEmailSuccessfullySent() {
                app.givenSetup()
                        .body("")
                        .post(format("/v1/api/accounts/%s/charges/%s/resend-confirmation-email", testBaseExtension.getAccountId(), chargeId))
                        .then()
                        .statusCode(204);
            }
        }
    }

    @Nested
    @DisplayName("Given a service id and account type")
    class ByServiceIdAndAccountType {

        @TestInstance(PER_CLASS)
        @Nested
        @DisplayName("Then resend confirmation email")
        class ResendConfirmationEmail {

            @Test
            @DisplayName("Should return 204 when successful")
            void shouldReturn204WhenEmailSuccessfullySent() {
                app.givenSetup()
                        .body("")
                        .contentType(APPLICATION_JSON)
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", testAccount.getServiceId(), testAccount.getGatewayAccountType(), chargeId))
                        .then()
                        .statusCode(204);
            }

            @ParameterizedTest
            @DisplayName("Should return 404 when gateway account not found")
            @MethodSource("shouldReturn404_argsProvider")
            void shouldReturn404_WhenServiceIdNotFound_OrAccountTypeNotFound(String serviceId, GatewayAccountType gatewayAccountType, String expectedError) {
                app.givenSetup()
                        .body("")
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", serviceId, gatewayAccountType, chargeId))
                        .then()
                        .statusCode(404)
                        .body("message", contains(expectedError));
            }

            private Stream<Arguments> shouldReturn404_argsProvider() {
                return Stream.of(
                        Arguments.of("not-this-service-id", testAccount.getGatewayAccountType(), "Gateway account not found for service ID [not-this-service-id] and account type [test]"),
                        Arguments.of(testAccount.getServiceId(), GatewayAccountType.LIVE, format("Gateway account not found for service ID [%s] and account type [live]", testAccount.getServiceId()))
                );
            }
        }
    }

    private ChargeUtils.ExternalChargeId createNewCharge(String transactionId, String paymentProvider) {
        return ChargeUtils.createNewChargeWithAccountId(
                ChargeStatus.CREATED,
                transactionId,
                testBaseExtension.getAccountId(),
                app.getDatabaseTestHelper(),
                EMAIL_ADDRESS,
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
