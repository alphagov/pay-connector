package uk.gov.pay.connector.it;

import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.PostgresDockerRule;

import static com.jayway.restassured.RestAssured.given;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PaymentRequestTest {
    private String configFilePath = resourceFilePath("config/test-it-config.yaml");

    private PostgresDockerRule postgres = new PostgresDockerRule();

    private DropwizardAppRule<ConnectorConfiguration> app = new DropwizardAppRule<>(
            ConnectorApp.class,
            configFilePath,
            config("database.url", postgres.getConnectionUrl()),
            config("database.user", postgres.getUsername()),
            config("database.password", postgres.getPassword()));

    @Rule
    public RuleChain rules = RuleChain.outerRule(postgres).around(app);

    @Test
    public void makePaymentAndRetrieveAmount() throws Exception {
        app.getApplication().run("db", "migrate", configFilePath);

        int expectedAmount = 2113;
        String payId = given().port(app.getLocalPort())
                .contentType("application/json")
                .body(String.format("{\"amount\":%d}", expectedAmount))
                .post("/api/payment")
                .then()
                .statusCode(200)
                .extract()
                .path("pay_id");

        int amount = given().port(app.getLocalPort())
                .get("/frontend/payment/" + payId)
                .then()
                .statusCode(200)
                .extract()
                .path("amount");

        assertThat(amount, is(expectedAmount));
    }
}
