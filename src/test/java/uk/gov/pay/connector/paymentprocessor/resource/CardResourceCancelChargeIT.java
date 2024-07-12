package uk.gov.pay.connector.paymentprocessor.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class CardResourceCancelChargeIT {

    @RegisterExtension
    private static final AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static final String SERVICE_ID = RandomIdGenerator.randomUuid();
    private static final String SERVICE_NAME = "Buy a Civil Servant a Coffee";
    private static final GatewayAccountType GATEWAY_ACCOUNT_TYPE = GatewayAccountType.TEST;
    private static final PaymentGatewayName PAYMENT_GATEWAY_NAME = PaymentGatewayName.STRIPE;
    private static String gatewayAccountId;
    
    public static Stream<ChargeStatus> invalidChargeStatusesForChargeCreation() { 
        return Stream.of(
                ChargeStatus.AUTHORISATION_REJECTED,
                ChargeStatus.AUTHORISATION_ERROR,
                ChargeStatus.CAPTURE_READY,
                ChargeStatus.CAPTURED,
                ChargeStatus.CAPTURE_SUBMITTED,
                ChargeStatus.CAPTURE_ERROR,
                ChargeStatus.EXPIRED,
                ChargeStatus.EXPIRE_CANCEL_FAILED,
                ChargeStatus.SYSTEM_CANCEL_ERROR,
                ChargeStatus.SYSTEM_CANCELLED,
                ChargeStatus.USER_CANCEL_READY,
                ChargeStatus.USER_CANCELLED,
                ChargeStatus.USER_CANCEL_ERROR
        );
    }

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
    }

    @Nested
    @DisplayName("Given a gateway account id")
    class ByAccountId {

        @Nested
        @DisplayName("Then cancel charge")
        class CancelCharge {

            @Test
            @DisplayName("Should return 204 when successful")
            void success_shouldReturn204() {
                var chargeId = createNewCharge();
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", gatewayAccountId, chargeId))
                        .then().statusCode(204);

                await()
                        .pollDelay(Duration.ofSeconds(1))
                        .atMost(Duration.ofSeconds(5)).until(() -> {
                            var chargeStatus = app.givenSetup()
                                    .get(String.format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeId))
                                    .then()
                                    .extract().path("state.status");
                            assertThat(chargeStatus, is("cancelled"));
                            return true;
                        });
                
                
            }

            @Test
            @DisplayName("Should return 202 if cancel operation is already in progress")
            void success_shouldReturn202() {
                var cancelSubmittedChargeId = ChargeUtils.createNewChargeWithAccountId(ChargeStatus.SYSTEM_CANCEL_SUBMITTED, gatewayAccountId, app.getDatabaseTestHelper(), PAYMENT_GATEWAY_NAME.getName());
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", gatewayAccountId, cancelSubmittedChargeId))
                        .then().statusCode(202);
            }

            @ParameterizedTest()
            @MethodSource("uk.gov.pay.connector.paymentprocessor.resource.CardResourceCancelChargeIT#invalidChargeStatusesForChargeCreation")
            @DisplayName("Should return 400 if charge is not in correct state")
            void badChargeState_shouldReturn400(ChargeStatus chargeStatus) {
                var badStateChargeId = ChargeUtils.createNewChargeWithAccountId(chargeStatus, gatewayAccountId, app.getDatabaseTestHelper(), PAYMENT_GATEWAY_NAME.getName());
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", gatewayAccountId, badStateChargeId))
                        .then().statusCode(400)
                        .body("message", contains(String.format("Charge not in correct state to be processed, %s", badStateChargeId)));
            }

            @Test
            @DisplayName("Should return 404 if charge is not found")
            void chargeNotFound_shouldReturn404() {
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", gatewayAccountId, "not-a-real-charge-id"))
                        .then().statusCode(404)
                        .body("message", contains(String.format("Charge with id [%s] not found.", "not-a-real-charge-id")));
            }

            @Test
            @DisplayName("Should return 404 if account is not found")
            void accountNotFound_shouldReturn404() {
                var chargeId = createNewCharge();
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", MAX_VALUE, chargeId))
                        .then().statusCode(404)
                        .body("message", contains(String.format("Charge with id [%s] not found.", chargeId)));
            }

            @Test
            @DisplayName("Should return 404 if account is non numeric")
            void nonNumericAccount_shouldReturn404() {
                var chargeId = createNewCharge();
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/accounts/%s/charges/%s/cancel", "invalid-account-id", chargeId))
                        .then().statusCode(404)
                        .body("message", is("HTTP 404 Not Found"));
            }
        }
    }

    @Nested
    @DisplayName("Given a service id and account type")
    class ByServiceIdAndAccountType {

        @Nested
        @DisplayName("Then cancel charge")
        class CancelCharge {

            @Test
            @DisplayName("Should return 204 when successful")
            void success_shouldReturn204() {
                var chargeId = createNewCharge();
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GATEWAY_ACCOUNT_TYPE, chargeId))
                        .then().statusCode(204);

                await()
                        .pollDelay(Duration.ofSeconds(1))
                        .atMost(Duration.ofSeconds(5)).until(() -> {
                            var chargeStatus = app.givenSetup()
                                    .get(String.format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeId))
                                    .then()
                                    .extract().path("state.status");
                            assertThat(chargeStatus, is("cancelled"));
                            return true;
                        });
            }

            @Test
            @DisplayName("Should return 202 if cancel operation is already in progress")
            void success_shouldReturn202() {
                var cancelSubmittedChargeId = ChargeUtils.createNewChargeWithAccountId(ChargeStatus.SYSTEM_CANCEL_SUBMITTED, gatewayAccountId, app.getDatabaseTestHelper(), PAYMENT_GATEWAY_NAME.getName());
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GATEWAY_ACCOUNT_TYPE, cancelSubmittedChargeId))
                        .then().statusCode(202);
            }

            @ParameterizedTest()
            @MethodSource("uk.gov.pay.connector.paymentprocessor.resource.CardResourceCancelChargeIT#invalidChargeStatusesForChargeCreation")
            @DisplayName("Should return 400 if charge is not in correct state")
            void badChargeState_shouldReturn400(ChargeStatus chargeStatus) {
                var badStateChargeId = ChargeUtils.createNewChargeWithAccountId(chargeStatus, gatewayAccountId, app.getDatabaseTestHelper(), PAYMENT_GATEWAY_NAME.getName());
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GATEWAY_ACCOUNT_TYPE, badStateChargeId))
                        .then().statusCode(400)
                        .body("message", contains(String.format("Charge not in correct state to be processed, %s", badStateChargeId)));
            }

            @Test
            @DisplayName("Should return 404 if charge is not found")
            void chargeNotFound_shouldReturn404() {
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", SERVICE_ID, GATEWAY_ACCOUNT_TYPE, "not-a-real-charge-id"))
                        .then().statusCode(404)
                        .body("message", contains(String.format("Charge with id [%s] not found.", "not-a-real-charge-id")));
            }

            @Test
            @DisplayName("Should return 404 if account is not found")
            void accountNotFound_shouldReturn404() {
                var chargeId = createNewCharge();
                app.givenSetup()
                        .body("")
                        .post(String.format("/v1/api/service/%s/account/%s/charges/%s/cancel", "not-real-service-id", GATEWAY_ACCOUNT_TYPE, chargeId))
                        .then().statusCode(404)
                        .body("message", contains(String.format("Charge with id [%s] not found.", chargeId)));
            }
        }
    }

    private static String createNewCharge() {
        return app.givenSetup()
                .body(createChargePostBody(gatewayAccountId))
                .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                .then().extract().path("charge_id");
    }
}
