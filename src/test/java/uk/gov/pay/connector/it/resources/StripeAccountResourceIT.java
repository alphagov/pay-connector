package uk.gov.pay.connector.it.resources;

import com.stripe.Stripe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeAccountResourceIT {
    
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    
    public static GatewayAccountResourceITBaseExtensions testBaseExtension = new GatewayAccountResourceITBaseExtensions("stripe", app.getLocalPort());

    private static final String STRIPE_ACCOUNT_ID = "acct_123example123";
    
    static {
        Stripe.overrideApiBase("http://localhost:" + app.getStripeWireMockServer().port());
    }

    @Test
    void requestingAStripeTestAccount_willCreateAStripeAccount_AndDisableExistingSandboxAccount() throws Exception {
        app.getStripeMockClient().mockCreateStripeTestConnectAccount();
        app.getStripeMockClient().mockCreateExternalAccount();
        app.getStripeMockClient().mockRetrieveAccount();
        app.getStripeMockClient().mockRetrievePersonCollection();
        app.getStripeMockClient().mockCreateRepresentativeForConnectAccount();
        
        String serviceId = RandomIdGenerator.randomUuid();
        app.setupSandboxGatewayAccount(serviceId, "Ollivander's wand shop");

        app.givenSetup().post(format("/v1/service/%s/request-stripe-test-account", serviceId))
                .then().statusCode(200);
        
        // TODO Verify: 
        // Stripe test account created in connector.
        // Old sandbox account set to be disabled.

        app.getStripeWireMockServer()
                .verify(postRequestedFor(urlEqualTo("/v1/accounts")));

        app.getStripeWireMockServer()
                .verify(postRequestedFor(urlEqualTo("/v1/accounts/acct_123/external_accounts")));
        
        app.getStripeWireMockServer()
                .verify(postRequestedFor(urlEqualTo("/v1/accounts/acct_123/persons")));
    }
    
    @Test
    void shouldNotBeAbleToRequestTestStripeAccountIfAlreadyExists() {
        // TODO
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
            String accountId = testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "stripe");
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
            testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "sandbox");

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-account")
                    .then()
                    .statusCode(404)
                    .body("message[0]", is("Gateway account for service ID [a-valid-service-id] and account type [test] is not a Stripe account"));
        }

        @Test
        void returnsNotFoundResponseWhenGatewayAccountCredentialsAreEmpty() {
            testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "stripe");

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-account")
                    .then()
                    .statusCode(404)
                    .body("message", is("Stripe gateway account for service ID [a-valid-service-id] and account type [test] does not have Stripe credentials"));
        }
    }
}
