package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;
import junitparams.Parameters;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.GatewayAccountPayload;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.ACCOUNTS_FRONTEND_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.createAGatewayAccountFor;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase.extractGatewayAccountId;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargesApiResourceAllowWebPaymentsIT {
    
    private static ObjectMapper objectMapper = new ObjectMapper();

    private DatabaseFixtures.TestAccount testAccount;
    private DatabaseTestHelper databaseTestHelper;
    private DatabaseFixtures databaseFixtures;
    private Long accountId;
    private String chargeId;
    private Long credentialsId;

    @DropwizardTestContext
    protected TestContext testContext;



    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        testAccount = addGatewayAccountAndCredential("worldpay");
        accountId = testAccount.getAccountId();
        chargeId = createCharge(testContext.getPort(), accountId.toString());

        credentialsId = testAccount.getCredentials().get(0).getId();
    }

    @Test
    public void assertApplePayPermission() throws JsonProcessingException {
        assertAppleAndGooglePayAreDisabledByDefault();

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
        assertAppleAndGooglePayAreDisabledByDefault();

        if (setAllowGooglePayFlag) allowWebPaymentsOnGatewayAccount("allow_google_pay");
        
        if (setGatewayMerchantId) addGatewayMerchantIdToGatewayAccount();

        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_google_pay", is(isGooglePayAllowed));
    }

    @Test
    public void assertBadRequestResponseIfPatchingDigitalWalletWithNonSupportedGateway() throws JsonProcessingException {
        assertAppleAndGooglePayAreDisabledByDefault();

        String accountIdWithNotDigitalWalletSupportedGateway = extractGatewayAccountId(createAGatewayAccountFor(testContext.getPort(), "epdq"));
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", "allow_apple_pay",
                "value", true));
        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountIdWithNotDigitalWalletSupportedGateway)
                .then()
                .body("message", contains("Gateway epdq does not support digital wallets."))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()))
                .and()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    private void assertAppleAndGooglePayAreDisabledByDefault() {
        given().port(testContext.getPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_apple_pay", is(false))
                .body("gateway_account.allow_google_pay", is(false));
    }

    private void allowWebPaymentsOnGatewayAccount(String path) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", path,
                "value", true));

        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    private void addGatewayMerchantIdToGatewayAccount() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(
                List.of(Map.of("op", "replace",
                "path", "credentials/gateway_merchant_id",
                "value", "94b53bf6b12b6c5")));

        given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .patch("/v1/api/accounts/" + accountId + "/credentials/" + credentialsId)
                .then()
                .statusCode(HttpStatus.SC_OK);
    }
    
    private String createCharge(int port, String accountId) {
        return given().port(port).contentType(JSON)
                .contentType(JSON)
                .body(createChargePostBody(accountId))
                .post(format("/v1/api/accounts/%s/charges", accountId))
                .then()
                .extract()
                .path("charge_id");
    }

    private DatabaseFixtures.TestAccount addGatewayAccountAndCredential(String paymentProvider) {
        long accountId = nextLong(2, 10000);
        LocalDateTime createdDate = LocalDate.parse("2021-01-01").atStartOfDay();
        LocalDateTime activeStartDate = LocalDate.parse("2021-02-01").atStartOfDay();
        LocalDateTime activeEndDate = LocalDate.parse("2021-03-01").atStartOfDay();

        AddGatewayAccountCredentialsParams credentialsParams = anAddGatewayAccountCredentialsParams()
                .withGatewayAccountId(accountId)
                .withPaymentProvider(paymentProvider)
                .withCreatedDate(createdDate.toInstant(ZoneOffset.UTC))
                .withActiveStartDate(activeStartDate.toInstant(ZoneOffset.UTC))
                .withActiveEndDate(activeEndDate.toInstant(ZoneOffset.UTC))
                .withState(GatewayAccountCredentialState.ACTIVE)
                .withCredentials(Map.of(
                                ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                        CREDENTIALS_MERCHANT_CODE, "a-merchant-code",
                                        CREDENTIALS_USERNAME, "a-username",
                                        CREDENTIALS_PASSWORD, "a-password")))
                .build();

        return databaseFixtures.aTestAccount().withPaymentProvider(paymentProvider)
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .insert();
    }
}
