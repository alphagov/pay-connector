package uk.gov.pay.connector.it.resources.worldpay;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithFullAddress;
import static uk.gov.pay.connector.rules.WorldpayMockClient.WORLDPAY_URL;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = ConnectorApp.class,
        config = "config/test-it-config.yaml",
        withDockerSQS = true
)
public class WorldpayCardResourceCardSslIT extends ChargingITestBase {
    
    private static String ACCOUNT_ID_THAT_APPEARS_IN_CONFIG_AS_WORLDPAY_CARD_SSL_ACCOUNTS = "42";

    public WorldpayCardResourceCardSslIT() {
        super("worldpay", ACCOUNT_ID_THAT_APPEARS_IN_CONFIG_AS_WORLDPAY_CARD_SSL_ACCOUNTS);
    }

    @Test
    public void shouldAuthoriseUsingCardSsl() {
        String chargeId = createNewChargeWithNoTransactionIdForSpecfiedGatewayAccount(ENTERING_CARD_DETAILS, ACCOUNT_ID_THAT_APPEARS_IN_CONFIG_AS_WORLDPAY_CARD_SSL_ACCOUNTS);
        worldpayMockClient.mockAuthorisationSuccess();

        String authDetails = buildJsonAuthorisationDetailsWithFullAddress();

        givenSetup()
                .body(authDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());

        verifyRequestBodyToWorldpay(WORLDPAY_URL);
    }

    private void verifyRequestBodyToWorldpay(String path) {
        wireMockServer.verify(
                postRequestedFor(urlPathEqualTo(path))
                        .withHeader("Content-Type", equalTo("application/xml"))
                        .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
                        .withRequestBody(matchingXPath(getMatchingXPath("paymentService", "merchantCode", "merchant-id")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("description", "Test description")))
                        .withRequestBody(matchingXPath(getMatchingXPath("amount", "value", "6234")))
                        .withRequestBody(matchingXPath(getMatchingXPath("amount", "currencyCode", "GBP")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("CARD-SSL//cardHolderName", "Scrooge McDuck")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("CARD-SSL//cardNumber", "4242424242424242")))
                        .withRequestBody(matchingXPath(getMatchingXPath("CARD-SSL//date", "month", "11")))
                        .withRequestBody(matchingXPath(getMatchingXPath("CARD-SSL//date", "year", "2099")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("CARD-SSL//cvc", "123")))
        );
    }

    public String getMatchingXPath(String path, String attribute, String value) {
        return format("//%s[@%s=\"%s\"]", path, attribute, value);
    }

    public String getMatchingXPathForText(String path, String value) {
        return format("//%s[text()=\"%s\"]", path, value);
    }

}
