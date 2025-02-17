package uk.gov.pay.connector.charge.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceResendConfirmationEmailIT {
    @RegisterExtension
    private static final AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            config("notifyConfig.emailNotifyEnabled", "true")
    );

    private static final String EMAIL_ADDRESS = "redshirt@example.com";
    private static final String SERVICE_NAME = "USS Cerritos Away Mission Signup";
    private static final String SERVICE_ID = RandomIdGenerator.randomUuid();
    private static final GatewayAccountType GATEWAY_ACCOUNT_TYPE = GatewayAccountType.TEST;
    private static final PaymentGatewayName PAYMENT_GATEWAY_NAME = PaymentGatewayName.STRIPE;
    
    private static String gatewayAccountId;
    private static String chargeId;

    @BeforeEach
    void setup() {
        gatewayAccountId = app.givenSetup()
                .body(toJson(Map.of(
                        "service_id", SERVICE_ID,
                        "type", GATEWAY_ACCOUNT_TYPE,
                        "payment_provider", PAYMENT_GATEWAY_NAME.getName(),
                        "service_name", SERVICE_NAME
                )))
                .post("/v1/api/accounts")
                .then().extract().path("gateway_account_id");

        chargeId = app.givenSetup()
                .body(createChargePostBody(gatewayAccountId, EMAIL_ADDRESS))
                .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                .then().extract().path("charge_id");

        app.givenSetup()
                .body(toJson(Map.of("op", "replace", "path", "/payment_confirmed/enabled", "value", true)))
                .patch(format("/v1/api/accounts/%s/email-notification", gatewayAccountId))
                .then().statusCode(200);

        app.givenSetup()
                .body(toJson(Map.of("op", "replace", "path", "/payment_confirmed/template_body", "value", "my cool template")))
                .patch(format("/v1/api/accounts/%s/email-notification", gatewayAccountId))
                .then().statusCode(200);
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
                app.getNotifyStub().respondWithSuccess();
                
                app.givenSetup()
                        .body("")
                        .post(format("/v1/api/accounts/%s/charges/%s/resend-confirmation-email", gatewayAccountId, chargeId))
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
                app.getNotifyStub().respondWithSuccess();
                
                app.givenSetup()
                        .body("")
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", SERVICE_ID, GATEWAY_ACCOUNT_TYPE, chargeId))
                        .then()
                        .statusCode(204);
            }

            @ParameterizedTest
            @DisplayName("Should return 404 when gateway account not found")
            @MethodSource("shouldReturn404_argsProvider")
            void shouldReturn404_WhenServiceIdNotFound_OrAccountTypeNotFound(String serviceId, GatewayAccountType gatewayAccountType, String expectedError) {
                app.getNotifyStub().respondWithSuccess();
                
                app.givenSetup()
                        .body("")
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", serviceId, gatewayAccountType, chargeId))
                        .then()
                        .statusCode(404)
                        .body("message", contains(expectedError));
            }
            
            @Test
            @DisplayName("Should return 402 when notification fails to send")
            void shouldReturn402_whenNotificationFails() {
                app.getNotifyStub().respondWithFailure();
                
                app.givenSetup()
                        .body("")
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/resend-confirmation-email", SERVICE_ID, GATEWAY_ACCOUNT_TYPE, chargeId))
                        .then()
                        .statusCode(402)
                        .body("message", contains("Failed to send email"));
            }

            private Stream<Arguments> shouldReturn404_argsProvider() {
                return Stream.of(
                        Arguments.of("not-this-service-id", GatewayAccountType.TEST, "Gateway account not found for service external id [not-this-service-id] and account type [test]"),
                        Arguments.of(SERVICE_ID, GatewayAccountType.LIVE, format("Gateway account not found for service external id [%s] and account type [live]", SERVICE_ID))
                );
            }
        }
    }
}
