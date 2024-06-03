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
import static io.restassured.RestAssured.given;
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
    private static final NotifyClientFactory NOTIFY_CLIENT_FACTORY = mock(NotifyClientFactory.class);
    private static final NotificationClient NOTIFICATION_CLIENT = mock(NotificationClient.class);
    
    @RegisterExtension
    private static final AppWithPostgresAndSqsExtension APP = new AppWithPostgresAndSqsExtension(
            ChargesApiResourceResendConfirmationEmailIT.ConnectorAppWithCustomInjector.class,
            config("notifyConfig.emailNotifyEnabled", "true")
    );
    
    @RegisterExtension
    private static final ITestBaseExtension TEST_BASE_EXTENSION = new ITestBaseExtension("sandbox", APP.getLocalPort(), APP.getDatabaseTestHelper());

    private static final String EMAIL_ADDRESS = "a@b.com";
    private DatabaseFixtures.TestAccount testAccount;
    private ChargeUtils.ExternalChargeId chargeId;

    @BeforeAll
    static void before() {
        when(NOTIFY_CLIENT_FACTORY.getInstance()).thenReturn(NOTIFICATION_CLIENT);
    }

    @BeforeEach
    void setup() throws NoSuchAlgorithmException, NotificationClientException {
        SecureRandom secureRandom = SecureRandom.getInstanceStrong();
        testAccount = TEST_BASE_EXTENSION.getTestAccount();
        chargeId = createNewCharge(String.valueOf(secureRandom.nextInt()), TEST_BASE_EXTENSION.getPaymentProvider());
        APP.getDatabaseTestHelper().addEmailNotification(Long.valueOf(TEST_BASE_EXTENSION.getAccountId()), "a template", true, PAYMENT_CONFIRMED);
        SendEmailResponse mockResponse = mock(SendEmailResponse.class);
        when(mockResponse.getNotificationId()).thenReturn(UUID.randomUUID());
        when(NOTIFICATION_CLIENT.sendEmail(any(), eq(EMAIL_ADDRESS), anyMap(), eq(null), any())).thenReturn(mockResponse);
    }
    
    @Nested
    @DisplayName("Given a resend confirmation email request")
    class ResendConfirmationEmail {
        @Nested
        @DisplayName("When account id is provided")
        class ByAccountId {
            @Test
            @DisplayName("Then 204 is received when successful")
            void shouldReturn204WhenEmailSuccessfullySent() {
                given().port(APP.getLocalPort())
                        .body("")
                        .contentType(APPLICATION_JSON)
                        .post(format("/v1/api/accounts/%s/charges/%s/resend-confirmation-email", TEST_BASE_EXTENSION.getAccountId(), chargeId))
                        .then()
                        .statusCode(204);
            }
        }

        @TestInstance(PER_CLASS)
        @Nested
        @DisplayName("When service id and account type are provided")
        class ByServiceIdAndAccountType {
            @Test
            @DisplayName("Then 204 is received when successful")
            void shouldReturn204WhenEmailSuccessfullySent() {
                given().port(APP.getLocalPort())
                        .body("")
                        .contentType(APPLICATION_JSON)
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", testAccount.getServiceId(), testAccount.getGatewayAccountType(), chargeId))
                        .then()
                        .statusCode(204);
            }

            @ParameterizedTest
            @DisplayName("Then 404 is received when gateway account not found")
            @MethodSource
            void shouldReturn404_WhenServiceIdNotFound_OrAccountTypeNotFound(String serviceId, GatewayAccountType gatewayAccountType, String expectedError) {
                given().port(APP.getLocalPort())
                        .body("")
                        .contentType(APPLICATION_JSON)
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", serviceId, gatewayAccountType, chargeId))
                        .then()
                        .statusCode(404)
                        .body("message", contains(expectedError));
            }

            private Stream<Arguments> shouldReturn404_WhenServiceIdNotFound_OrAccountTypeNotFound() {
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
                TEST_BASE_EXTENSION.getAccountId(),
                APP.getDatabaseTestHelper(),
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
            return NOTIFY_CLIENT_FACTORY;
        }
    }
}
