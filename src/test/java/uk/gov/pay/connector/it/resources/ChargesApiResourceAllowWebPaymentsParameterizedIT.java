package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.it.util.ChargeUtils.createChargePostBody;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

@RunWith(Parameterized.class)
public class ChargesApiResourceAllowWebPaymentsParameterizedIT extends NewChargingITestBase {

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Parameterized.Parameter(value = 0)
    public boolean setAllowGooglePayFlag;
    @Parameterized.Parameter(value = 1)
    public boolean setGatewayMerchantId;
    @Parameterized.Parameter(value = 2)
    public boolean isGooglePayAllowed;

    private DatabaseFixtures.TestAccount testAccount;
    private DatabaseTestHelper databaseTestHelper;
    private DatabaseFixtures databaseFixtures;
    private Long accountId;
    private String chargeId;
    private Long credentialsId;

    public ChargesApiResourceAllowWebPaymentsParameterizedIT() {
        super("worldpay");
    }


    @Before
    public void setup() {
        databaseTestHelper = connectorApp.getDatabaseTestHelper();
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        testAccount = addGatewayAccountAndCredential("worldpay");
        accountId = testAccount.getAccountId();
        chargeId = createCharge(connectorApp.getLocalPort(), accountId.toString());

        credentialsId = testAccount.getCredentials().get(0).getId();
    }

    // Each element in the inner array will be assigned to a variable of the test class, annotated by @Parameterized.Parameter(value = 0), etc.
    // The test will be run for each set, using different values.
    // { setAllowGooglePayFlag, setGatewayMerchantId, isGooglePayAllowed }
    @Parameterized.Parameters()
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                { true, false, false }, { true, true, true }, { false, true, false }
        });
    }

    @Test
    public void assertGooglePayPermission() throws JsonProcessingException {
        assertAppleAndGooglePayAreDisabledByDefault();

        if (setAllowGooglePayFlag) allowWebPaymentsOnGatewayAccount("allow_google_pay");
        
        if (setGatewayMerchantId) addGatewayMerchantIdToGatewayAccount();

        given().port(connectorApp.getLocalPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_google_pay", is(isGooglePayAllowed));
    }

    private void assertAppleAndGooglePayAreDisabledByDefault() {
        given().port(connectorApp.getLocalPort()).contentType(JSON)
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .body("gateway_account.allow_apple_pay", is(false))
                .body("gateway_account.allow_google_pay", is(false));
    }

    private void allowWebPaymentsOnGatewayAccount(String path) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                "path", path,
                "value", true));

        given().port(connectorApp.getLocalPort()).contentType(JSON)
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

        given().port(connectorApp.getLocalPort()).contentType(JSON)
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
