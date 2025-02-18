package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountResourceSwitchPspIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();


    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Nested
    class ByServiceIdAndAccountType {

        private String serviceExternalId;
        private String worldpayCredentialExternalId;
        private String stripeCredentialExternalId;
        private DatabaseFixtures.TestAccount liveGatewayAccount;
        private DatabaseFixtures.TestAccount testGatewayAccount;
        
        @BeforeEach
        void setUp() {
            serviceExternalId = randomUuid();
            liveGatewayAccount = withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withType(GatewayAccountType.LIVE)
                    .withServiceId(serviceExternalId);
            testGatewayAccount = withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withType(GatewayAccountType.TEST)
                    .withServiceId(serviceExternalId);
            worldpayCredentialExternalId = randomUuid();
            stripeCredentialExternalId = randomUuid();
        }
        
        @Test
        void shouldSwitchPaymentProviderFromStripeToWorldpay_andHandlePreexistingSandboxAccount() {
            var sandboxTestCredentialExternalId = randomUuid();
            var stripeTestCredentialExternalId = randomUuid();
            
            testGatewayAccount
                    .withDescription("A Stripe TEST account")
                    .withGatewayAccountCredentials(List.of(
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(testGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(stripeTestCredentialExternalId)
                                    .withCreatedDate(Instant.now())
                                    .withActiveStartDate(Instant.now())
                                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                                    .build(),
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(testGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(sandboxTestCredentialExternalId)
                                    .withCreatedDate(Instant.now().minus(30L, ChronoUnit.DAYS))
                                    .withActiveStartDate(Instant.now().minus(30L, ChronoUnit.DAYS))
                                    .withPaymentProvider(PaymentGatewayName.SANDBOX.getName())
                                    .build()
                    ))
                    .insert();

            liveGatewayAccount
                    .withDescription("A Stripe LIVE account")
                    .withProviderSwitchEnabled(true)
                    .withGatewayAccountCredentials(List.of(
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(liveGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(stripeCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                                    .build(),
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(liveGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT)
                                    .withExternalId(worldpayCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                                    .build()
                    ))
                    .insert();

            String switchPspPayload = toJson(Map.of("user_external_id", "some-user-external-id",
                    "gateway_account_credential_external_id", worldpayCredentialExternalId));

            app.givenSetup()
                    .body(switchPspPayload)
                    .post(format("/v1/api/service/%s/account/%s/switch-psp", serviceExternalId, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s", serviceExternalId, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("provider_switch_enabled", is(false))
                    .body("description", is("A Worldpay LIVE account"))
                    .body("gateway_account_credentials.size()", is(2))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", worldpayCredentialExternalId),
                            is(GatewayAccountCredentialState.ACTIVE.toString()))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", stripeCredentialExternalId),
                            is(GatewayAccountCredentialState.RETIRED.toString()));

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s", serviceExternalId, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("description", is("A Sandbox TEST account"))
                    .body("gateway_account_credentials.size()", is(2))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", stripeTestCredentialExternalId),
                            is(GatewayAccountCredentialState.RETIRED.toString()))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", sandboxTestCredentialExternalId),
                            is(GatewayAccountCredentialState.ACTIVE.toString()));
        }

        @Test
        void shouldSwitchPaymentProviderFromStripeToWorldpay_andHandleNonStripeTestAccount() {
            var sandboxTestCredentialExternalId = randomUuid();

            testGatewayAccount
                    .withDescription("A Sandbox TEST account, not a Stripe TEST account")
                    .withGatewayAccountCredentials(List.of(
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(testGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(sandboxTestCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.SANDBOX.getName())
                                    .build()
                    ))
                    .insert();

            liveGatewayAccount
                    .withDescription("A Stripe LIVE account")
                    .withProviderSwitchEnabled(true)
                    .withGatewayAccountCredentials(List.of(
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(liveGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(stripeCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                                    .build(),
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(liveGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT)
                                    .withExternalId(worldpayCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                                    .build()
                    ))
                    .insert();

            String switchPspPayload = toJson(Map.of("user_external_id", "some-user-external-id",
                    "gateway_account_credential_external_id", worldpayCredentialExternalId));

            app.givenSetup()
                    .body(switchPspPayload)
                    .post(format("/v1/api/service/%s/account/%s/switch-psp", serviceExternalId, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s", serviceExternalId, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("provider_switch_enabled", is(false))
                    .body("description", is("A Worldpay LIVE account"))
                    .body("gateway_account_credentials.size()", is(2))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", worldpayCredentialExternalId),
                            is(GatewayAccountCredentialState.ACTIVE.toString()))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", stripeCredentialExternalId),
                            is(GatewayAccountCredentialState.RETIRED.toString()));

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s", serviceExternalId, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("description", is("A Sandbox TEST account, not a Stripe TEST account"))
                    .body("gateway_account_credentials.size()", is(1))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", sandboxTestCredentialExternalId),
                            is(GatewayAccountCredentialState.ACTIVE.toString()));
            
        }

        @Test
        void shouldSwitchPaymentProviderFromStripeToWorldpay_andHandleStripeTestAccount() {
            var stripeTestCredentialExternalId = randomUuid();

            testGatewayAccount
                    .withDescription("A Stripe TEST account")
                    .withGatewayAccountCredentials(List.of(
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(testGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(stripeTestCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                                    .build()
                    ))
                    .insert();
            
            liveGatewayAccount
                    .withDescription("A Stripe LIVE account")
                    .withProviderSwitchEnabled(true)
                    .withGatewayAccountCredentials(List.of(
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(liveGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(stripeCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                                    .build(),
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(liveGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT)
                                    .withExternalId(worldpayCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                                    .build()
                    ))
                    .insert();
            
            String switchPspPayload = toJson(Map.of("user_external_id", "some-user-external-id",
                    "gateway_account_credential_external_id", worldpayCredentialExternalId));

            app.givenSetup()
                    .body(switchPspPayload)
                    .post(format("/v1/api/service/%s/account/%s/switch-psp", serviceExternalId, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s", serviceExternalId, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("provider_switch_enabled", is(false))
                    .body("description", is("A Worldpay LIVE account"))
                    .body("gateway_account_credentials.size()", is(2))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", worldpayCredentialExternalId),
                            is(GatewayAccountCredentialState.ACTIVE.toString()))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", stripeCredentialExternalId),
                            is(GatewayAccountCredentialState.RETIRED.toString()));

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s", serviceExternalId, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("description", is("A Sandbox TEST account"))
                    .body("gateway_account_credentials.size()", is(2))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", stripeTestCredentialExternalId),
                            is(GatewayAccountCredentialState.RETIRED.toString()))
                    .body(String.format("gateway_account_credentials.find { it.state == '%s' }.payment_provider", GatewayAccountCredentialState.ACTIVE),
                            is(PaymentGatewayName.SANDBOX.getName()));
        }

        @Test
        void switchPaymentProviderFromWorldpayToStripeSuccessfully() {
            testGatewayAccount
                    .withProviderSwitchEnabled(true)
                    .withGatewayAccountCredentials(List.of(
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(testGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.ACTIVE)
                                    .withExternalId(worldpayCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                                    .build(),
                            anAddGatewayAccountCredentialsParams()
                                    .withGatewayAccountId(testGatewayAccount.getAccountId())
                                    .withState(GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT)
                                    .withExternalId(stripeCredentialExternalId)
                                    .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                                    .build()
                    ))
                    .withType(GatewayAccountType.TEST)
                    .insert();
            
            String switchPspPayload = toJson(Map.of("user_external_id", "some-user-external-id",
                    "gateway_account_credential_external_id", stripeCredentialExternalId));

            app.givenSetup()
                    .body(switchPspPayload)
                    .post(format("/v1/api/service/%s/account/%s/switch-psp", serviceExternalId, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s", serviceExternalId, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("provider_switch_enabled", is(false))
                    .body("gateway_account_credentials.size()", is(2))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", worldpayCredentialExternalId),
                            is(GatewayAccountCredentialState.RETIRED.toString()))
                    .body(String.format("gateway_account_credentials.find { it.external_id == '%s' }.state", stripeCredentialExternalId),
                            is(GatewayAccountCredentialState.ACTIVE.toString()));
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
                    anAddGatewayAccountCredentialsParams()
                            .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                            .withCredentials(Map.of())
                            .withExternalId(activeExtId)
                            .withState(GatewayAccountCredentialState.ACTIVE)
                            .build();

            AddGatewayAccountCredentialsParams switchToParams =
                    anAddGatewayAccountCredentialsParams()
                            .withGatewayAccountId(Long.valueOf(gatewayAccountId))
                            .withCredentials(Map.of())
                            .withExternalId(switchToExtId)
                            .withState(GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT)
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
            assertThat(retiredCredentials.get("state").toString(), is(GatewayAccountCredentialState.RETIRED.toString()));

            Map<String, Object> activeCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialByExternalId(switchToExtId);
            assertThat(activeCredentials.get("state").toString(), is(GatewayAccountCredentialState.ACTIVE.toString()));
        }
    }
}
