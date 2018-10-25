package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CreateGatewayAccountResourceTest extends GatewayAccountResourceTestBase {

    @Test
    @Parameters({"sandbox", "worldpay", "smartpay", "epdq"})
    public void createAGatewayAccount(String provider) {
        createAGatewayAccountFor(provider, "my test service", "analhtics");
    }
    
    @Test
    public void createStripeGatewayAccountWithoutCredentials() throws Exception {
        GatewayAccountRequest input = new GatewayAccountRequest("test", "stripe", "My shiny new stripe service", null, null, null);
        givenSetup()
                .body(new ObjectMapper().writeValueAsString(input))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON)
                .body("gateway_account_id", not(isEmptyString()))
                .body("service_name", is("My shiny new stripe service"))
                .body("type", is("test"))
                .body("links.size()", is(1))
                .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }
    
    @Test
    public void createStripeGatewayAccountWithCredentials() throws Exception {
        StripeCredentials credentials = new StripeCredentials("abc");
        GatewayAccountRequest input = new GatewayAccountRequest("test", "stripe", "My shiny new stripe service", null, null, credentials);
        givenSetup()
                .body(new ObjectMapper().writeValueAsString(input))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON)
                .body("gateway_account_id", not(isEmptyString()))
                .body("service_name", is("My shiny new stripe service"))
                .body("type", is("test"))
                .body("links.size()", is(1))
                .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }
    
    //TODO get account /v1/frontend/accounts/{accountId} returns stripe credentials
}
