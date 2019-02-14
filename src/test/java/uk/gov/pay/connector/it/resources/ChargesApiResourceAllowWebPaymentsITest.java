package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.restassured.response.Response;
import junitparams.Parameters;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.GatewayAccountPayload;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.ACCOUNTS_FRONTEND_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.createAGatewayAccountFor;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.extractGatewayAccountId;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargesApiResourceAllowWebPaymentsITest {
    
    private String accountId;
    private String chargeId;

    @DropwizardTestContext
    protected TestContext testContext;
    
    @Before
    public void setup() {
        accountId = extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), "worldpay"));
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        updateGatewayAccountCredentialsWith(testContext.getPort(), accountId, gatewayAccountPayload.buildCredentialsPayload());
        chargeId = createCharge(testContext.getPort(), accountId);
        assertAppleAndGooglePayAreDisabledByDefault();
    }

    private void assertAppleAndGooglePayAreDisabledByDefault() {
        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_apple_pay", is(false))
                .body("gateway_account.allow_google_pay", is(false));
    }

    @Test
    public void assertApplePayPermission() throws JsonProcessingException {
        allowWebPaymentsOnGatewayAccount("allow_apple_pay");

        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_apple_pay", is(true));
    }
    
    @Test
    @Parameters({
            "true, false, false", 
            "true, true, true", 
            "false, true, false"})
    public void assertGooglePayPermission(boolean setAllowGooglePayFlag, boolean setGatewayMerchantId, boolean isGooglePayAllowed) throws JsonProcessingException {
        if (setAllowGooglePayFlag) allowWebPaymentsOnGatewayAccount("allow_google_pay");
        
        if (setGatewayMerchantId) addGatewayMerchantIdToGatewayAccount(accountId);

        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_google_pay", is(isGooglePayAllowed));
    }
    
    @Test
    public void assertBadRequestResponseIfPatchingMerchantIdWithNoGatewayAccountCredentials() throws JsonProcessingException {
        String accountIdWithoutGatewayAccountCredentials = extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), "worldpay"));
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "add",
                "path", "credentials/gateway_merchant_id",
                "value", "94b53bf6b12b6c5"));
        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountIdWithoutGatewayAccountCredentials)
                .then()
                .body("message", is("Account Credentials are required to set a Gateway Merchant ID"))
                .and()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    private void allowWebPaymentsOnGatewayAccount(String path) throws JsonProcessingException {
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", path,
                "value", "true"));

        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    private void addGatewayMerchantIdToGatewayAccount(String accountId) throws JsonProcessingException {
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "add",
                "path", "credentials/gateway_merchant_id",
                "value", "94b53bf6b12b6c5"));

        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    public static Response updateGatewayAccountCredentialsWith(int port, String accountId, Map<String, Object> credentials) {
        return given().port(port).contentType(JSON).accept(JSON)
                .body(credentials)
                .patch(ACCOUNTS_FRONTEND_URL + accountId + "/credentials");
    }
    
    public static String createCharge(int port, String accountId) {
        return given().port(port).contentType(JSON)
                .contentType(JSON)
                .body(createChargePostBody(accountId))
                .post(format("/v1/api/accounts/%s/charges", accountId))
                .then()
                .extract()
                .path("charge_id");
    }
}
