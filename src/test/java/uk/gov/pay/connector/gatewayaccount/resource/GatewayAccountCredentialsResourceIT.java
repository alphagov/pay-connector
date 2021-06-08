package uk.gov.pay.connector.gatewayaccount.resource;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.specification.RequestSpecification;
import junitparams.Parameters;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.HashMap;
import java.util.Map;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountCredentialsResourceIT {
    private DatabaseFixtures.TestAccount testAccount;

    private static final String UPDATE_3DS_FLEX_CREDENTIALS_URL = "/v1/api/accounts/%s/3ds-flex-credentials";
    private static final String VALIDATE_3DS_FLEX_CREDENTIALS_URL = "/v1/api/accounts/%s/worldpay/check-3ds-flex-config";
    private static final String VALIDATE_WORLDPAY_CREDENTIALS_URL = "/v1/api/accounts/%s/worldpay/check-credentials";

    public static final String VALID_ISSUER = "53f0917f101a4428b69d5fb0"; // pragma: allowlist secret`
    public static final String VALID_ORG_UNIT_ID = "57992a087a0c4849895ab8a2"; // pragma: allowlist secret`
    public static final String VALID_JWT_MAC_KEY = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9"; // pragma: allowlist secret`

    @DropwizardTestContext
    protected TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;
    private WireMockServer wireMockServer;
    private WorldpayMockClient worldpayMockClient;
    private DatabaseFixtures databaseFixtures;
    private Long accountId;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        accountId = nextLong(2, 10000);
        databaseTestHelper = testContext.getDatabaseTestHelper();
        wireMockServer = testContext.getWireMockServer();
        worldpayMockClient = new WorldpayMockClient(wireMockServer);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        testAccount = databaseFixtures.aTestAccount().withPaymentProvider("worldpay")
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .withCredentials(Map.of("merchant_id", "a-merchant-id", "username", "a-username", "password", "a-password"))
                .insert();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort()).contentType(JSON);
    }

    @Test
    public void validate_valid_3ds_flex_credentials() throws Exception {
        wireMockServer.stubFor(post("/shopper/3ds/ddc.html").willReturn(ok()));

        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200)
                .body("result", is("valid"));
    }

    @Test
    public void validate_invalid_3ds_flex_credentials() throws Exception {
        wireMockServer.stubFor(post("/shopper/3ds/ddc.html").willReturn(badRequest()));

        var invalidIssuer = "54a0917b10ca4428b69d5ed0"; // pragma: allowlist secret`
        var invalidOrgUnitId = "57002a087a0c4849895ab8a2"; // pragma: allowlist secret`
        var invalidJwtMacKey = "3751b5f1-4fef-4306-bc09-99df6320d5b8"; // pragma: allowlist secret`
        var payload = objectMapper.writeValueAsString(Map.of(
                "issuer", invalidIssuer,
                "organisational_unit_id", invalidOrgUnitId,
                "jwt_mac_key", invalidJwtMacKey));

        givenSetup()
                .body(payload)
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200)
                .body("result", is("invalid"));
    }

    @Test
    public void should_return_503_if_error_communicating_with_3ds_flex_ddc_endpoint() throws Exception {
        wireMockServer.stubFor(post("/shopper/3ds/ddc.html").willReturn(serverError()));

        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
    }
    
    @Test
    public void should_return_404_when_validating_3ds_flex_credentials_if_gateway_account_does_not_exist() throws Exception {
        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, accountId-1))
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }
    
    @Test
    public void should_return_422_if_organisation_unit_id_not_in_correct_format() throws Exception {
        var payload = objectMapper.writeValueAsString(Map.of("issuer", VALID_ISSUER,
                "organisational_unit_id", "incorrectFormat",
                "jwt_mac_key", VALID_JWT_MAC_KEY));

        verifyIncorrectFormat(payload, "Field [organisational_unit_id] must be 24 lower-case hexadecimal characters");
    }
    
    @Test
    public void should_return_422_if_organisation_unit_id_is_null() throws Exception {
        var jsonFields = new HashMap<String, String>();
        jsonFields.put("issuer", VALID_ISSUER);
        jsonFields.put("organisational_unit_id", null);
        jsonFields.put("jwt_mac_key", VALID_JWT_MAC_KEY);
        var payload = objectMapper.writeValueAsString(jsonFields);

        verifyIncorrectFormat(payload, "Field [organisational_unit_id] must be 24 lower-case hexadecimal characters");
    }

    @Test
    public void should_return_422_if_issuer_not_in_correct_format() throws Exception {
        var payload = objectMapper.writeValueAsString(Map.of("issuer", "44992i087n0v4849895al9i3",
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", VALID_JWT_MAC_KEY));

        verifyIncorrectFormat(payload, "Field [issuer] must be 24 lower-case hexadecimal characters");
    }
    
    @Test
    public void should_return_422_if_issuer_is_null() throws Exception {
        var jsonFields = new HashMap<String, String>();
        jsonFields.put("issuer", null);
        jsonFields.put("organisational_unit_id", VALID_ORG_UNIT_ID);
        jsonFields.put("jwt_mac_key", VALID_JWT_MAC_KEY);
        var payload = objectMapper.writeValueAsString(jsonFields);

        verifyIncorrectFormat(payload, "Field [issuer] must be 24 lower-case hexadecimal characters");
    }

    @Test
    public void should_return_422_if_jwt_mac_key_not_in_correct_format() throws Exception {
        var payload = objectMapper.writeValueAsString(Map.of("issuer", VALID_ISSUER,
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", "hihihihi"));

        verifyIncorrectFormat(payload, "Field [jwt_mac_key] must be a UUID in its lowercase canonical representation");
    }
    
    @Test
    public void should_return_422_if_jwt_mac_key_is_null() throws Exception {
        var jsonFields = new HashMap<String, String>();
        jsonFields.put("issuer", VALID_ISSUER);
        jsonFields.put("organisational_unit_id", VALID_ORG_UNIT_ID);
        jsonFields.put("jwt_mac_key", null);
        var payload = objectMapper.writeValueAsString(jsonFields);

        verifyIncorrectFormat(payload, "Field [jwt_mac_key] must be a UUID in its lowercase canonical representation");
    }

    private void verifyIncorrectFormat(String payload, String expectedErrorMessage) {
        givenSetup()
                .body(payload)
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("error_identifier", is(ErrorIdentifier.GENERIC.name()))
                .body("message[0]", is(expectedErrorMessage));
    }

    @Test
    public void setWorldpay3dsFlexCredentialsWhenThereAreNonExisting() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "issuer", VALID_ISSUER,
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", VALID_JWT_MAC_KEY
        ));
        givenSetup()
                .body(payload)
                .post(format(UPDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200);
        var result = databaseTestHelper.getWorldpay3dsFlexCredentials(accountId);
        assertThat(result.get("issuer"), is(VALID_ISSUER));
        assertThat(result.get("organisational_unit_id"), is(VALID_ORG_UNIT_ID));
        assertThat(result.get("jwt_mac_key"), is(VALID_JWT_MAC_KEY));
    }

    @Test
    public void overrideSetWorldpay3dsCredentials() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "issuer", "53f0917f101a4428b69d5fb0", // pragma: allowlist secret`
                "organisational_unit_id", "57992a087a0c4849895ab8a2", // pragma: allowlist secret`
                "jwt_mac_key", "3751b5f1-4fef-4306-bc09-99df6320d5b8" // pragma: allowlist secret`
        ));
        givenSetup()
                .body(payload)
                .post(format(UPDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200);

        String newIssuer = "43f0917f101a4428b69d5fb9"; // pragma: allowlist secret`
        String newOrgUnitId = "44992a087a0c4849895cc9a3"; // pragma: allowlist secret`
        String updatedJwtMacKey = "512ee2a9-4a3e-46d4-86df-8e2ac3d6a6a8"; // pragma: allowlist secret`
        payload = objectMapper.writeValueAsString(Map.of(
                "issuer", newIssuer,
                "organisational_unit_id", newOrgUnitId,
                "jwt_mac_key", updatedJwtMacKey
        ));
        givenSetup()
                .body(payload)
                .post(format(UPDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200);
        var result = databaseTestHelper.getWorldpay3dsFlexCredentials(accountId);
        assertThat(result.get("issuer"), is(newIssuer));
        assertThat(result.get("organisational_unit_id"), is(newOrgUnitId));
        assertThat(result.get("jwt_mac_key"), is(updatedJwtMacKey));
    }

    @Test
    @Parameters({
            "jwt_mac_key, Field [jwt_mac_key] must be a UUID in its lowercase canonical representation", 
            "issuer, Field [issuer] must be 24 lower-case hexadecimal characters",
            "organisational_unit_id, Field [organisational_unit_id] must be 24 lower-case hexadecimal characters",
    })
    public void update3dsFlexCredentials_missingFieldsReturnCorrectError(String key, String expectedErrorMessage) throws JsonProcessingException {
        Map<String, String> jsonFields = new HashMap<>();
        jsonFields.put("issuer", "57992a087a0c4849895ab8a2"); // pragma: allowlist secret`
        jsonFields.put("organisational_unit_id", "57992a087a0c4849895ab8a2"); // pragma: allowlist secret`
        jsonFields.put("jwt_mac_key", "512ee2a9-4a3e-46d4-86df-8e2ac3d6a6a8"); // pragma: allowlist secret`
        jsonFields.remove(key);

        givenSetup()
                .body(objectMapper.writeValueAsString(jsonFields))
                .post(format(UPDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(422)
                .body("message[0]", is(expectedErrorMessage));
    }

    @Test
    public void update3dsFlexCredentials_nonExistentGatewayAccountReturns404() throws JsonProcessingException {
        Long fakeAccountId = RandomUtils.nextLong();
        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(UPDATE_3DS_FLEX_CREDENTIALS_URL, fakeAccountId))
                .then()
                .statusCode(404)
                .body("message[0]", is("Not a Worldpay gateway account"));
    }

    @Test
    public void update3dsFlexCredentials_nonWorldpayGatewayAccountReturns404() throws JsonProcessingException {
        long fakeAccountId = RandomUtils.nextLong();
        databaseFixtures.aTestAccount().withPaymentProvider("smartpay")
                .withIntegrationVersion3ds(2)
                .withAccountId(fakeAccountId)
                .insert();
        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(UPDATE_3DS_FLEX_CREDENTIALS_URL, fakeAccountId))
                .then()
                .statusCode(404)
                .body("message[0]", is("Not a Worldpay gateway account"));
    }

    @Test
    public void checkWorldpayCredentials_nonWorldpayGatewayAccountReturns404() throws JsonProcessingException {
        long accountId = RandomUtils.nextLong();
        databaseFixtures.aTestAccount().withAccountId(accountId).withPaymentProvider("smartpay").insert();
        givenSetup()
                .body(getValidWorldpayCredentials())
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(404)
                .body("message[0]", is(format("Gateway account with id %s is not a Worldpay account.", accountId)));
    }

    @Test
    public void checkWorldpayCredentials_nonExistentGatewayAccountReturns404() throws JsonProcessingException {
        long accountId = RandomUtils.nextLong();
        givenSetup()
                .body(getValidWorldpayCredentials())
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(404)
                .body("message[0]", is(format("Gateway Account with id [%s] not found.", accountId)));
    }

    @Test
    public void checkWorldpayCredentials_returns422WhenUsernameMissing() throws JsonProcessingException {
        long accountId = RandomUtils.nextLong();
        String body = objectMapper.writeValueAsString(Map.of(
                "username","",
                "password", "valid-password",
                "merchant_id", "valid-merchant-id"
        ));
        givenSetup()
                .body(body)
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(422)
                .body("message[0]", is("Field [username] is required"));
    }

    @Test
    public void checkWorldpayCredentials_returns422WhenPasswordMissing() throws JsonProcessingException {
        long accountId = RandomUtils.nextLong();
        String body = objectMapper.writeValueAsString(Map.of(
                "username","valid-username",
                "password", "",
                "merchant_id", "valid-merchant-id"
        ));
        givenSetup()
                .body(body)
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(422)
                .body("message[0]", is("Field [password] is required"));
    }

    @Test
    public void checkWorldpayCredentials_returns422WhenMerchantIdMissing() throws JsonProcessingException {
        long accountId = RandomUtils.nextLong();
        String body = objectMapper.writeValueAsString(Map.of(
                "username","valid-username",
                "password", "valid-password",
                "merchant_id", ""
        ));
        givenSetup()
                .body(body)
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(422)
                .body("message[0]", is("Field [merchant_id] is required"));
    }

    @Test
    public void checkWorldpayCredentials_returnsValid() throws JsonProcessingException {
        worldpayMockClient.mockCredentialsValidationValid();

        long accountId = RandomUtils.nextLong();
        databaseFixtures.aTestAccount().withAccountId(accountId).withPaymentProvider("worldpay").insert();
        givenSetup()
                .body(getValidWorldpayCredentials())
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(200)
                .body("result", is("valid"));
    }

    @Test
    public void checkWorldpayCredentials_returnsInvalid() throws JsonProcessingException {
        worldpayMockClient.mockCredentialsValidationInvalid();

        long accountId = RandomUtils.nextLong();
        databaseFixtures.aTestAccount().withAccountId(accountId).withPaymentProvider("worldpay").insert();
        givenSetup()
                .body(getValidWorldpayCredentials())
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(200)
                .body("result", is("invalid"));
    }

    @Test
    public void checkWorldpayCredentials_returns500WhenWorldpayReturnsUnexpectedResponse() throws JsonProcessingException {
        worldpayMockClient.mockCredentialsValidationUnexpectedResponse();

        long accountId = RandomUtils.nextLong();
        databaseFixtures.aTestAccount().withAccountId(accountId).withPaymentProvider("worldpay").insert();
        givenSetup()
                .body(getValidWorldpayCredentials())
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(500)
                .body("message[0]", is("Worldpay returned an unexpected response when validating credentials"));
    }

    @Test
    public void createGatewayAccountsCredentialsWithCredentials_responseShouldBe200_Ok() {
        Map credentials = Map.of("stripe_account_id", "some-account-id");
        givenSetup()
                .body(toJson(Map.of("payment_provider", "stripe", "credentials", credentials)))
                .post("/v1/api/accounts/" + accountId + "/credentials")
                .then()
                .statusCode(OK.getStatusCode());

        givenSetup()
                .get("/v1/frontend/accounts/" + accountId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("gateway_account_credentials.size()", is(2))
                .body("gateway_account_credentials[1].payment_provider", is("stripe"))
                .body("gateway_account_credentials[1].credentials.stripe_account_id", is("some-account-id"));;
    }

    @Test
    public void createGatewayAccountsCredentialsMissingAccount_responseShouldBe404() {
        givenSetup()
                .body(toJson(Map.of("payment_provider", "worldpay")))
                .post("/v1/api/accounts/10000/credentials")
                .then()
                .statusCode(404);
    }

    @Test
    public void createGatewayAccountsCredentialsValidatesRequestBusinessLogic_responseShouldBe400() {
        givenSetup()
                .body(toJson(Map.of("payment_provider", "epdq")))
                .post("/v1/api/accounts/10000/credentials")
                .then()
                .statusCode(400);
    }

    private String getCheck3dsConfigPayloadForValidCredentials() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "issuer", VALID_ISSUER,
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", VALID_JWT_MAC_KEY));
    }
    
    private String getValidWorldpayCredentials() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "username","valid-user-name",
                "password", "valid-password",
                "merchant_id", "valid-merchant-id"
        ));
    }
}
