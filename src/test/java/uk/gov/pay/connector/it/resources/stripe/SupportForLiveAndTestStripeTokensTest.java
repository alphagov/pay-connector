package uk.gov.pay.connector.it.resources.stripe;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import junitparams.Parameters;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.base.ChargingITestBase.authoriseChargeUrlFor;
import static uk.gov.pay.connector.it.base.ChargingITestBase.cancelChargeUrlFor;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SupportForLiveAndTestStripeTokensTest {
    
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);
    private StripeMockClient stripeMockClient = new StripeMockClient();
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

        WireMock.reset();
        stripeMockClient.mockCreateToken();
        stripeMockClient.mockCreateSource();
        stripeMockClient.mockCreateCharge();
        stripeMockClient.mockCancelCharge();
    }

    @Test
    @Parameters({"TEST, Bearer sk_test", "LIVE, Bearer sk_live"}) //sk_test and sk_live are defined in test-it-config.yaml
    public void assertUsageOfCorrectToken(String accountType, String expectedAuthHeader) {
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider, credentials, Type.fromString(accountType));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId));

        verify(postRequestedFor(urlEqualTo("/v1/tokens"))
                .withHeader(AUTHORIZATION, equalTo(expectedAuthHeader)));

        verify(postRequestedFor(urlEqualTo("/v1/sources"))
                .withHeader(AUTHORIZATION, equalTo(expectedAuthHeader)));

        verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader(AUTHORIZATION, equalTo(expectedAuthHeader)));

        given().port(testContext.getPort())
                .contentType(JSON)
                .post(cancelChargeUrlFor(accountId, externalChargeId));

        verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                .withHeader(AUTHORIZATION, equalTo(expectedAuthHeader)));
    }

    private String addCharge() {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, 100L, ENTERING_CARD_DETAILS,
                "RETURN_URL", null, "a description");
        return externalChargeId;
    }
}
