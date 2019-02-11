package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.GatewayAccountPayload;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.it.resources.GatewayAccountFrontendResourceITest.updateGatewayAccountCredentialsWith;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.createAGatewayAccountFor;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.extractGatewayAccountId;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayMerchantIdITest {

    @DropwizardTestContext
    protected TestContext testContext;

    @Test
    public void replaceGatewayMerchantId() throws Exception {
        String accountId = setUpAccount();
        addGatewayMerchantIdToAccount(accountId, "1234abc");
        replaceGatewayMerchantId(accountId, "abc1234");
        assertGatewayMerchantIdOnCharge(accountId, "abc1234");
    }
    
    @Test
    public void addGatewayMerchantIdToGatewayAccount() throws Exception {
        String accountId = setUpAccount();
        addGatewayMerchantIdToAccount(accountId, "1234abc");
        assertGatewayMerchantIdOnCharge(accountId, "1234abc");
    }

    private void assertGatewayMerchantIdOnCharge(String accountId, String gatewayMerchantId) {
        String chargeId = given().port(testContext.getPort())
                .contentType(JSON)
                .contentType(JSON)
                .body(createChargePostBody(accountId))
                .post(format("/v1/api/accounts/%s/charges", accountId))
                .then()
                .extract()
                .path("charge_id");

        given().port(testContext.getPort())
                .contentType(JSON)
                .contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.gateway_merchant_id", is(gatewayMerchantId));
    }

    private void replaceGatewayMerchantId(String accountId, String gatewayMerchantId) throws JsonProcessingException {
        patch(accountId, gatewayMerchantId, "replace");
    }

    private void patch(String accountId, String gatewayMerchantId, String op) throws JsonProcessingException {
        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", op,
                "path", "credentials/gateway_merchant_id",
                "value", gatewayMerchantId));
        
        given().port(testContext.getPort())
                .contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    private void addGatewayMerchantIdToAccount(String accountId, String gatewayMerchantId) throws JsonProcessingException {
        patch(accountId, gatewayMerchantId, "add");
    }
    
    private String setUpAccount() {
        String accountId = extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), "worldpay"));
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        updateGatewayAccountCredentialsWith(testContext.getPort(), accountId, gatewayAccountPayload.buildCredentialsPayload());
        return accountId;
    }
}
