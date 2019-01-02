package uk.gov.pay.connector.it.resources.stripe;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.LIVE;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlFor;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config-without-stripe-live-token.yaml")
public class MissingLiveStripeTokenTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);
    private DatabaseTestHelper databaseTestHelper;
    private String paymentProvider = STRIPE.getName();
    private String accountId;
    private Map<String, String> credentials;
    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4242424242424242", "Visa");

    @DropwizardTestContext
    private TestContext testContext;

    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());
        credentials = ImmutableMap.of("stripe_account_id", String.valueOf(RandomUtils.nextInt()));
    }

    @Test
    public void shouldErrorWhenAuthorisingAChargeForALiveGatewayAccount() {
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider, credentials, LIVE);

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(500)
                .body("message", containsString("There was an internal server error authorising charge external id: " + externalChargeId));
    }

    private String addCharge() {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, Long.valueOf(100L), ENTERING_CARD_DETAILS,
                "RETURN_URL", null, "a description");
        return externalChargeId;
    }
}
