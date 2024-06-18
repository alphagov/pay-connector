package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITBaseExtensions.ACCOUNTS_API_URL;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountResourceSwitchPspIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    class ByServiceIdAndAccountType {

        private final String serviceId = RandomIdGenerator.newId();

        @Test
        void switchPaymentProviderFromWorldpayToStripeSuccessfully() {
            setupWorldpayGatewayAccount();
            String worldpayCredentialsId = setupCredentialsForWorldpayGatewayAccount();
            updateProviderSwitchEnabledOnWorldpayGatewayAccount();
            verifyWorldpayCredentialsIsActiveAndProviderSwitchIsEnabled();
            String stripeCredentialsExternalId = addStripeCredentialsOnTheGatewayAccount();

            String switchPspPayload = toJson(Map.of("user_external_id", "some-user-external-id",
                    "gateway_account_credential_external_id", stripeCredentialsExternalId));

            app.givenSetup()
                    .body(switchPspPayload)
                    .post(format("/v1/api/service/%s/account/test/switch-psp", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("provider_switch_enabled", is(false))
                    .body("gateway_account_credentials[0].state", is("RETIRED"))
                    .body("gateway_account_credentials[0].external_id", is(worldpayCredentialsId))
                    .body("gateway_account_credentials[1].state", is("ACTIVE"))
                    .body("gateway_account_credentials[1].external_id", is(stripeCredentialsExternalId));
        }

        private String addStripeCredentialsOnTheGatewayAccount() {
            String stripeCredentialsExternalId = app.givenSetup()
                    .body(toJson(Map.of("payment_provider", "stripe",
                            "credentials", Map.of("stripe_account_id", "some-account-id"))))
                    .post(format("/v1/api/service/%s/account/test/credentials", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .extract()
                    .path("external_id");

            app.givenSetup()
                    .body(toJson(List.of(
                            Map.of("op", "replace",
                                    "path", "state",
                                    "value", "VERIFIED_WITH_LIVE_PAYMENT")
                    )))
                    .patch(format("/v1/api/service/%s/account/test/credentials/%s", serviceId, stripeCredentialsExternalId))
                    .then()
                    .statusCode(200)
                    .body("$", hasKey("credentials"))
                    .body("credentials.stripe_account_id", is("some-account-id"))
                    .body("state", CoreMatchers.is("VERIFIED_WITH_LIVE_PAYMENT"));
            
            return stripeCredentialsExternalId;
        }

        private void verifyWorldpayCredentialsIsActiveAndProviderSwitchIsEnabled() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("provider_switch_enabled", is(true))
                    .body("gateway_account_credentials", hasSize(1))
                    .body("gateway_account_credentials[0].payment_provider", is("worldpay"))
                    .body("gateway_account_credentials[0].state", is("ACTIVE"));
        }

        private void updateProviderSwitchEnabledOnWorldpayGatewayAccount() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace",
                            "path", "provider_switch_enabled",
                            "value", true)))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());
        }

        private String setupCredentialsForWorldpayGatewayAccount() {
            String worldpayCredentialsId = app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .extract()
                    .path("gateway_account_credentials[0].external_id");

            app.givenSetup()
                    .body(toJson(List.of(
                            Map.of("op", "replace",
                                    "path", "credentials/worldpay/one_off_customer_initiated",
                                    "value", Map.of("merchant_code", "new-merchant-code",
                                            "username", "new-username",
                                            "password", "new-password")))))
                    .patch(format("/v1/api/service/%s/account/test/credentials/%s", serviceId, worldpayCredentialsId))
                    .then()
                    .statusCode(200);
            
            return worldpayCredentialsId;
        }

        private void setupWorldpayGatewayAccount() {
            Map<String, String> gatewayAccountRequest = Map.of(
                    "payment_provider", "worldpay",
                    "service_id", serviceId,
                    "service_name", "Service Name",
                    "type", "test");

            app.givenSetup().body(toJson(gatewayAccountRequest)).post(ACCOUNTS_API_URL);
        }
    }
    
    @Nested
    class ByGatewayAccountId {
        
        @Test
        void shouldSwitchPaymentProvider() throws JsonProcessingException {
            String gatewayAccountId = "1000024";
            String activeExtId = randomUuid();
            String switchToExtId = randomUuid();

            AddGatewayAccountCredentialsParams activeParams =
                    AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder
                            .anAddGatewayAccountCredentialsParams()
                            .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                            .withCredentials(Map.of())
                            .withExternalId(activeExtId)
                            .withState(ACTIVE)
                            .build();

            AddGatewayAccountCredentialsParams switchToParams =
                    AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder
                            .anAddGatewayAccountCredentialsParams()
                            .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                            .withCredentials(Map.of())
                            .withExternalId(switchToExtId)
                            .withState(VERIFIED_WITH_LIVE_PAYMENT)
                            .withPaymentProvider("stripe")
                            .build();

            app.getDatabaseTestHelper().addGatewayAccount(
                    anAddGatewayAccountParams()
                            .withAccountId(gatewayAccountId)
                            .withPaymentGateway("stripe")
                            .withServiceName("a cool service")
                            .withProviderSwitchEnabled(true)
                            .withGatewayAccountCredentials(List.of(activeParams, switchToParams))
                            .build());

            String payload = objectMapper.writeValueAsString(Map.of("user_external_id", "some-user-external-id",
                    "gateway_account_credential_external_id", switchToExtId));

            app.givenSetup()
                    .body(payload)
                    .post("/v1/api/accounts/" + gatewayAccountId + "/switch-psp")
                    .then()
                    .statusCode(OK.getStatusCode());

            Map<String, Object> account = app.getDatabaseTestHelper().getGatewayAccount(Long.valueOf(gatewayAccountId));
            assertThat((Integer) account.get("integration_version_3ds"), is(2));
            assertThat((Boolean) account.get("provider_switch_enabled"), is(false));

            Map<String, Object> retiredCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialByExternalId(activeExtId);
            assertThat(retiredCredentials.get("state").toString(), is(RETIRED.name()));

            Map<String, Object> activeCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialByExternalId(switchToExtId);
            assertThat(activeCredentials.get("state").toString(), is(ACTIVE.name()));
        }
    }
}
