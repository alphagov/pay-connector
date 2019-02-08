package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;
import static uk.gov.pay.connector.it.util.gatewayaccount.GatewayAccountOperations.createAGatewayAccountFor;
import static uk.gov.pay.connector.it.util.gatewayaccount.GatewayAccountOperations.updateGatewayAccountCredentialsWith;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class AllowWebPaymentsITest {
    
    private String accountId;
    private String chargeId;

    @DropwizardTestContext
    protected TestContext testContext;
    protected DatabaseTestHelper databaseTestHelper;
    
    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = createAGatewayAccountFor(testContext.getPort(), "worldpay", databaseTestHelper);
        GatewayAccountFrontendResourceITest.GatewayAccountPayload gatewayAccountPayload = 
                GatewayAccountFrontendResourceITest.GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        updateGatewayAccountCredentialsWith(testContext.getPort(), accountId, gatewayAccountPayload.buildCredentialsPayload());
        chargeId = createCharge(testContext.getPort(), accountId);
    }
    
    @Test
    public void noWebPaymentsAllowedIfAllowWebPaymentsFlagIsFalse() {
        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allowWebPayments", is(false))
                .body("gateway_account.allow_apple_pay", is(false))
                .body("gateway_account.allow_google_pay", is(false));
    }
    
    @Test
    public void noWebPaymentsAllowedIfGatewayMerchantIdIsSetButAllowWebPaymentsFlagIsFalse() throws Exception {
        addGatewayMerchantIdToGatewayAccount();

        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allowWebPayments", is(false))
                .body("gateway_account.allow_apple_pay", is(false))
                .body("gateway_account.allow_google_pay", is(false));
    }

    @Test
    public void onlyAllowApplePayIfAllowWebPaymentsFlagIsTrue() throws Exception {
        allowWebPaymentsOnGatewayAccount();

        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allowWebPayments", is(true))
                .body("gateway_account.allow_apple_pay", is(true))
                .body("gateway_account.allow_google_pay", is(false));
    }

    @Test
    public void allowBothWebPaymentsIfAllowWebPaymentsFlagAndGatewayMerchantIdAreSet() throws Exception {
        allowWebPaymentsOnGatewayAccount();
        addGatewayMerchantIdToGatewayAccount();
        
        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allowWebPayments", is(true))
                .body("gateway_account.allow_apple_pay", is(true))
                .body("gateway_account.allow_google_pay", is(true));
    }

    private void allowWebPaymentsOnGatewayAccount() throws JsonProcessingException {
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "allow_web_payments",
                "value", "true"));

        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    private void addGatewayMerchantIdToGatewayAccount() throws JsonProcessingException {
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "add",
                "path", "credentials/gateway_merchant_id",
                "value", "1234abc"));

        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    private static String createCharge(int port, String accountId) {
        return given().port(port).contentType(JSON)
                .contentType(JSON)
                .body(createChargePostBody(accountId))
                .post(format("/v1/api/accounts/%s/charges", accountId))
                .then()
                .extract()
                .path("charge_id");
    }
}
