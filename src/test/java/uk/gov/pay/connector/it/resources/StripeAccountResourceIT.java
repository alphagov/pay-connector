package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Nested;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeAccountResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITBaseExtensions testBaseExtension = new GatewayAccountResourceITBaseExtensions("stripe", app.getLocalPort());

    private static final String STRIPE_ACCOUNT_ID = "acct_123example123";

    @Nested
    class GetStripeAccountByGatewayAccountId {
        @Test
        public void getStripeAccountReturnsSuccessfulResponse() {
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
        public void getStripeAccountReturnsNotFoundResponseWhenGatewayAccountNotFound() {
            app.givenSetup()
                    .get("/v1/api/accounts/1337/stripe-account")
                    .then()
                    .statusCode(404);
        }
    
        @Test
        public void getStripeAccountReturnsNotFoundResponseWhenGatewayAccountIsNotStripe() {
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
        public void getStripeAccountReturnsNotFoundResponseWhenGatewayAccountCredentialsAreEmpty() {
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
        public void getStripeAccountReturnsSuccessfulResponse() {
            String accountId = testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "stripe");
            Map<String, String> credentials = Map.of("stripe_account_id", STRIPE_ACCOUNT_ID);
            app.givenSetup()
                    .body(toJson(Map.of("payment_provider", "stripe", "credentials", credentials)))
                    .post("/v1/api/accounts/" + accountId + "/credentials");

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/TEST/stripe-account")
                    .then()
                    .statusCode(200)
                    .body("stripe_account_id", is(STRIPE_ACCOUNT_ID));
        }

        @Test
        public void getStripeAccountReturnsNotFoundResponseWhenServiceIdNotFound() {
            app.givenSetup()
                    .get("/v1/api/service/unknown-service-id/TEST/stripe-account")
                    .then()
                    .statusCode(404);
        }

        @Test
        public void getStripeAccountReturnsNotFoundResponseWhenNoStripeAccountExistsForService() {
            testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "sandbox");

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/TEST/stripe-account")
                    .then()
                    .statusCode(404);
        }

        @Test
        public void getStripeAccountReturnsNotFoundResponseWhenGatewayAccountCredentialsAreEmpty() {
            testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "stripe");

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/TEST/stripe-account")
                    .then()
                    .statusCode(404);
        }
    }
}
