package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;

import java.util.Collections;

import static org.hamcrest.Matchers.is;

public class StripeAccountResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private static final String STRIPE_ACCOUNT_ID = "acct_123example123";

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
