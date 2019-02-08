package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpStatus;
import org.junit.Ignore;
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
    public void shouldErrorIfAddingAGatewayMerchantIdWhenItAlreadyExists() {
        
    }
    
    @Ignore
    @Test
    public void updateGatewayAccountMerchantId() throws Exception {
        String accountId = extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), "worldpay"));
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        updateGatewayAccountCredentialsWith(testContext.getPort(), accountId, gatewayAccountPayload.buildCredentialsPayload());

        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "add",
                "path", "credentials/gateway_merchant_id",
                "value", "1234abc"));

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);

        payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "replace",
                "path", "credentials/gateway_merchant_id",
                "value", "abc1234"));

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId);

        String chargeId = given().port(testContext.getPort())
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
                .then().log().body()
                .body("gateway_account.gateway_merchant_id", is("abc1234"));
    }

    @Test
    public void updateGatewayAccountWithGatewayMerchantId() throws Exception {
        String accountId = extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), "worldpay"));
        GatewayAccountPayload gatewayAccountPayload = GatewayAccountPayload.createDefault().withMerchantId("a-merchant-id");
        updateGatewayAccountCredentialsWith(testContext.getPort(), accountId, gatewayAccountPayload.buildCredentialsPayload());

        String payload = new ObjectMapper().writeValueAsString(ImmutableMap.of("op", "add",
                "path", "credentials/gateway_merchant_id",
                "value", "1234abc"));

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);

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
                .then().log().body()
                .body("gateway_account.gateway_merchant_id", is("1234abc"));
    }
}
