package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.Collections;

import static org.hamcrest.Matchers.is;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeAccountResourceIT extends GatewayAccountResourceTestBase {

    private static final String STRIPE_ACCOUNT_ID = "acct_123example123";

    @Test
    public void getStripeAccountReturnsSuccessfulResponse() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .withPaymentProvider("stripe")
                .withCredentials(Collections.singletonMap("stripe_account_id", STRIPE_ACCOUNT_ID))
                .insert();

        givenSetup()
                .get("/v1/api/accounts/" + testAccount.getAccountId() + "/stripe-account")
                .then()
                .statusCode(200)
                .body("stripe_account_id", is(STRIPE_ACCOUNT_ID));
    }

    @Test
    public void getStripeAccountReturnsNotFoundResponseWhenGatewayAccountNotFound() {
        givenSetup()
                .get("/v1/api/accounts/1337/stripe-account")
                .then()
                .statusCode(404);
    }

    @Test
    public void getStripeAccountReturnsNotFoundResponseWhenGatewayAccountIsNotStripe() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .withPaymentProvider("sandbox")
                .withCredentials(Collections.singletonMap("stripe_account_id", STRIPE_ACCOUNT_ID))
                .insert();

        givenSetup()
                .get("/v1/api/accounts/" + testAccount.getAccountId() + "/stripe-account")
                .then()
                .statusCode(404);
    }

    @Test
    public void getStripeAccountReturnsNotFoundResponseWhenGatewayAccountCredentialsAreEmpty() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .withPaymentProvider("stripe")
                .withCredentials(Collections.emptyMap())
                .insert();

        givenSetup()
                .get("/v1/api/accounts/" + testAccount.getAccountId() + "/stripe-account")
                .then()
                .statusCode(404);
    }

}
