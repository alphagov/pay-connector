package uk.gov.pay.connector.it.resources;

import com.stripe.Stripe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.CreateGatewayAccountResponse;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITBaseExtensions.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeAccountResourceIT {
    
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    
    public static GatewayAccountResourceITBaseExtensions testBaseExtension = new GatewayAccountResourceITBaseExtensions(app.getLocalPort());

    private static final String STRIPE_ACCOUNT_ID = "acct_123example123";
    
    static {
        Stripe.overrideApiBase("http://localhost:" + app.getStripeWireMockServer().port());
    }

    @Test
    void requestingAStripeTestAccount_willCreateAStripeAccount_AndDisableExistingSandboxAccount() throws Exception {
        app.getStripeMockClient().mockCreateStripeTestConnectAccount(); // acct_123 is set statically in this mocked response
        app.getStripeMockClient().mockCreateExternalAccount();
        app.getStripeMockClient().mockRetrieveAccount();
        app.getStripeMockClient().mockRetrievePersonCollection();
        app.getStripeMockClient().mockCreateRepresentativeForConnectAccount();
        
        String serviceId = RandomIdGenerator.randomUuid();
        var createGatewayAccountResponse = setupSandboxGatewayAccount(serviceId, "Ollivander's wand shop");

        app.givenSetup().post(format("/v1/service/%s/request-stripe-test-account", serviceId))
                .then().statusCode(200)
                .body("stripe_connect_account_id", is("acct_123"))
                .body("gateway_account_id", is(notNullValue()))
                .body("gateway_account_id", not(createGatewayAccountResponse.gatewayAccountId()))
                .body("gateway_account_external_id", is(notNullValue()))
                .body("gateway_account_id", not(createGatewayAccountResponse.externalId()));
        
        // Assert API calls to Stripe
        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/accounts")));
        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/accounts/acct_123/external_accounts")));
        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/accounts/acct_123/persons")));

        // Assert Stripe test account created in connector
        app.givenSetup().get(format("/v1/api/service/%s/account/test", serviceId))
                .then()
                .statusCode(200)
                .body("gateway_account_id", not(createGatewayAccountResponse.gatewayAccountId()))
                .body("external_id", not(createGatewayAccountResponse.externalId()))
                .body("service_id", is(serviceId))
                .body("payment_provider", is("stripe"))
                .body("type", is("test"))
                .body("service_name", is("Ollivander's wand shop"))
                .body("description", is(createGatewayAccountResponse.description()))
                .body("analyticsId", is(createGatewayAccountResponse.analyticsId()))
                .body("location", is(createGatewayAccountResponse.location()))
                .body("requires3ds", is(createGatewayAccountResponse.requires3ds()))
                .body("links[0].href", not(createGatewayAccountResponse.links().get(0).get("href")))
                .body("gateway_account_credentials", hasSize(1))
                .body("gateway_account_credentials[0].payment_provider", is("stripe"))
                .body("gateway_account_credentials[0].state", is("ACTIVE"))
                .body("gateway_account_credentials[0].credentials.stripe_account_id", is("acct_123"))
                .body("gateway_account_credentials[0].external_id", is(notNullValue(String.class)));

        // Assert old sandbox account is disabled
        assertTrue((Boolean) app.getDatabaseTestHelper().getGatewayAccount(Long.valueOf(createGatewayAccountResponse.gatewayAccountId())).get("disabled"));
    }

    private CreateGatewayAccountResponse setupSandboxGatewayAccount(String serviceId, String serviceName) {
        return app.givenSetup().body(toJson(Map.of(
                        "service_id", serviceId,
                        "type", "test",
                        "payment_provider", "sandbox",
                        "service_name", serviceName)))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201)
                .extract().body().as(CreateGatewayAccountResponse.class);
    }
    
    @Test
    void shouldNotBeAbleToRequestTestStripeAccountIfAlreadyExists() {
        String serviceId = RandomIdGenerator.randomUuid();
        app.givenSetup().body(toJson(Map.of(
                        "service_id", serviceId,
                        "type", "test",
                        "payment_provider", "stripe",
                        "service_name", "Borgin and Burkes")))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201);

        app.givenSetup().post(format("/v1/service/%s/request-stripe-test-account", serviceId))
                .then().statusCode(409)
                .body("message", contains("Cannot request Stripe test account because a Stripe test account already exists."));
    }
    
    @Test
    void returnBadRequestIfSandboxGatewayAccountDoesNotExist() {
        String serviceId = RandomIdGenerator.randomUuid();
        app.givenSetup().body(toJson(Map.of(
                        "service_id", serviceId,
                        "type", "test",
                        "payment_provider", "worldpay",
                        "service_name", "Borgin and Burkes")))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201);

        app.givenSetup().post(format("/v1/service/%s/request-stripe-test-account", serviceId))
                .then().statusCode(409)
                .body("message", contains("Cannot request Stripe test account because existing test account is not a Sandbox one."));
    }
    
    @Test
    void returnNotFoundIfServiceDoesNotExist() {
        app.givenSetup().post("/v1/service/doesNotExist/request-stripe-test-account")
                .then().statusCode(404);
    }

    @Nested
    class GetStripeAccountByGatewayAccountId {
        @Test
        void returnsSuccessfulResponse() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .withPaymentProvider("stripe")
                    .withCredentials(Collections.singletonMap("stripe_account_id", STRIPE_ACCOUNT_ID))
                    .insert();
    
            app.givenSetup()
                    .get("/v1/api/accounts/" + testAccount.getAccountId() + "/stripe-account")
                    .then()
                    .statusCode(200)
                    .body("stripe_account_id", is(STRIPE_ACCOUNT_ID));
        }
    
        @Test
        void returnsNotFoundResponseWhenGatewayAccountNotFound() {
            app.givenSetup()
                    .get("/v1/api/accounts/1337/stripe-account")
                    .then()
                    .statusCode(404);
        }
    
        @Test
        void returnsNotFoundResponseWhenGatewayAccountIsNotStripe() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .withPaymentProvider("sandbox")
                    .withCredentials(Collections.singletonMap("stripe_account_id", STRIPE_ACCOUNT_ID))
                    .insert();
    
            app.givenSetup()
                    .get("/v1/api/accounts/" + testAccount.getAccountId() + "/stripe-account")
                    .then()
                    .statusCode(404);
        }
    
        @Test
        void returnsNotFoundResponseWhenGatewayAccountCredentialsAreEmpty() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .withPaymentProvider("stripe")
                    .withCredentials(Collections.emptyMap())
                    .insert();
    
            app.givenSetup()
                    .get("/v1/api/accounts/" + testAccount.getAccountId() + "/stripe-account")
                    .then()
                    .statusCode(404);
        }
    }
    
    @Nested
    class GetStripeAccountByServiceIdAndAccountType {
        @Test
        void returnsSuccessfulResponse() {
            String accountId = testBaseExtension.createAGatewayAccountAndExtractAccountId(
                    aCreateGatewayAccountPayloadBuilder()
                            .withServiceId("a-valid-service-id")
                            .withProvider("stripe")
                            .build());
            Map<String, String> credentials = Map.of("stripe_account_id", STRIPE_ACCOUNT_ID);
            app.givenSetup()
                    .body(toJson(Map.of("payment_provider", "stripe", "credentials", credentials)))
                    .post("/v1/api/accounts/" + accountId + "/credentials")
                    .then()
                    .statusCode(200);

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-account")
                    .then()
                    .statusCode(200)
                    .body("stripe_account_id", is(STRIPE_ACCOUNT_ID));
        }

        @Test
        void returnsNotFoundResponseWhenServiceIdNotFound() {
            app.givenSetup()
                    .get("/v1/api/service/unknown-service-id/account/TEST/stripe-account")
                    .then()
                    .statusCode(404)
                    .body("message[0]", is("Gateway account not found for service ID [unknown-service-id] and account type [test]"));
        }

        @Test
        void returnsNotFoundResponseWhenNoStripeAccountExistsForService() {
            testBaseExtension.createAGatewayAccount(
                    aCreateGatewayAccountPayloadBuilder()
                            .withServiceId("a-valid-service-id")
                            .withProvider("sandbox")
                            .build());

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-account")
                    .then()
                    .statusCode(404)
                    .body("message[0]", is("Gateway account for service ID [a-valid-service-id] and account type [test] is not a Stripe account"));
        }

        @Test
        void returnsNotFoundResponseWhenGatewayAccountCredentialsAreEmpty() {
            testBaseExtension.createAGatewayAccount(
                    aCreateGatewayAccountPayloadBuilder()
                            .withServiceId("a-valid-service-id")
                            .withProvider("stripe")
                            .build());

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-account")
                    .then()
                    .statusCode(404)
                    .body("message", is("Stripe gateway account for service ID [a-valid-service-id] and account type [test] does not have Stripe credentials"));
        }
    }
}
