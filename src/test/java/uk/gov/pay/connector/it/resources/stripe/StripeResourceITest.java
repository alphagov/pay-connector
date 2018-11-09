package uk.gov.pay.connector.it.resources.stripe;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeResourceITest {

    private static final String CVC = "123";
    private static final String EXP_MONTH = "11";
    private static final String EXP_YEAR = "99";
    private static final String CARD_NUMBER = "4444333322221111";
    private static final String AMOUNT = "6234";
    private static final String DESCRIPTION = "Test description";
    private static final String STRIPE_ACCOUNT_ID = "123";

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", CVC, EXP_MONTH + "/" + EXP_YEAR, "visa");
    private String paymentProvider = PaymentGatewayName.STRIPE.getName();
    private String accountId;
    private StripeMockClient stripeMockClient = new StripeMockClient();
    private DatabaseTestHelper databaseTestHelper;

    @DropwizardTestContext
    private TestContext testContext;
    
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);

    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());

        stripeMockClient.mockCreateToken();
        stripeMockClient.mockCreateCharge();
    }

    @Test
    public void authoriseCharge() {
        addGatewayAccount(ImmutableMap.of("stripe_account_id", STRIPE_ACCOUNT_ID));

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        verify(postRequestedFor(urlEqualTo("/v1/tokens"))
                .withHeader("Content-Type", equalTo(APPLICATION_FORM_URLENCODED))
                .withRequestBody(matching(constructExpectedRequestBody())));

        verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader("Content-Type", equalTo(APPLICATION_JSON)));

        List<LoggedRequest> requests = findAll(postRequestedFor(urlMatching("/v1/charges")));
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getBodyAsString()).isEqualTo(stripeAuthoriseJsonPayload());
    }

    private String constructExpectedRequestBody() {
        return format("card[cvc]=123&card[exp_month]=11&card[exp_year]=2099&card[number]=4444333322221111",
                CVC, EXP_MONTH, EXP_YEAR, CARD_NUMBER)
                .replace("[", "%5B")
                .replace("]", "%5D");
    }

    @Test
    public void shouldReturnInternalServerResponseWhenGatewayAccountHasNoStripeAccountId() {
        addGatewayAccount(emptyMap());

        String externalChargeId = addCharge();

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(500)
                .body("message", containsString("There is no stripe_account_id for gateway account with id"));
    }

    private String addCharge() {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, Long.valueOf(AMOUNT), ENTERING_CARD_DETAILS, "RETURN_URL", null, DESCRIPTION);
        return externalChargeId;
    }

    private void addGatewayAccount(Map credentials) {
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider, credentials);
    }

    private String authoriseChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/cards".replace("{chargeId}", chargeId);
    }
    private String stripeAuthoriseJsonPayload() {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", AMOUNT);
        params.put("currency", "GBP");
        params.put("description", DESCRIPTION);
        params.put("source", "src_1DT9bn2eZvKYlo2Cg5okt8WC");
        params.put("capture", false);
        Map<String, Object> destinationParams = new HashMap<>();
        destinationParams.put("account", STRIPE_ACCOUNT_ID);
        params.put("destination", destinationParams);
        return new JSONObject(params).toString();
    }
}
