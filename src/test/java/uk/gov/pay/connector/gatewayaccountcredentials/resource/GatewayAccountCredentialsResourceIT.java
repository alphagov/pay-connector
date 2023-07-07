package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.Gson;
import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.postgresql.util.PGobject;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountCredentialsResourceIT {
    private DatabaseFixtures.TestAccount testAccount;

    private static final String UPDATE_3DS_FLEX_CREDENTIALS_URL = "/v1/api/accounts/%s/3ds-flex-credentials";
    private static final String VALIDATE_3DS_FLEX_CREDENTIALS_URL = "/v1/api/accounts/%s/worldpay/check-3ds-flex-config";
    private static final String VALIDATE_WORLDPAY_CREDENTIALS_URL = "/v1/api/accounts/%s/worldpay/check-credentials";
    private static final String PATCH_CREDENTIALS_URL = "/v1/api/accounts/%s/credentials/%s";

    public static final String VALID_ISSUER = "53f0917f101a4428b69d5fb0"; // pragma: allowlist secret`
    public static final String VALID_ORG_UNIT_ID = "57992a087a0c4849895ab8a2"; // pragma: allowlist secret`
    public static final String VALID_JWT_MAC_KEY = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9"; // pragma: allowlist secret`

    @DropwizardTestContext
    protected TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;
    private WireMockServer wireMockServer;
    private WorldpayMockClient worldpayMockClient;
    private DatabaseFixtures databaseFixtures;
    private Long credentialsId;
    private String credentialsExternalId;
    private Long accountId;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        wireMockServer = testContext.getWireMockServer();
        worldpayMockClient = new WorldpayMockClient(wireMockServer);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);

        testAccount = addGatewayAccountAndCredential("worldpay", ACTIVE, TEST);
        accountId = testAccount.getAccountId();

        credentialsId = testAccount.getCredentials().get(0).getId();
        credentialsExternalId = testAccount.getCredentials().get(0).getExternalId();
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
    public void updateFlexCredentialsShouldSetGatewayAccountCredentialsStateToActiveForLiveAccount() throws JsonProcessingException {
        DatabaseFixtures.TestAccount testAccount = addGatewayAccountAndCredential("worldpay", CREATED, LIVE);
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

        var result = databaseTestHelper.getWorldpay3dsFlexCredentials(testAccount.getAccountId());
        assertThat(result.get("issuer"), is(VALID_ISSUER));
        assertThat(result.get("organisational_unit_id"), is(VALID_ORG_UNIT_ID));
        assertThat(result.get("jwt_mac_key"), is(VALID_JWT_MAC_KEY));

        List<Map<String, Object>> gatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsForAccount(testAccount.getAccountId());
        assertThat(gatewayAccountCredentials.get(0).get("state"), is("ACTIVE"));
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
    public void checkWorldpayCredentials_returns500WhenWorldpayReturnsUnexpectedResponse() throws JsonProcessingException {
        worldpayMockClient.mockCredentialsValidationUnexpectedResponse();

        long accountId = nextLong(2, 10000);
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
                .get("/v1/api/accounts/" + accountId)
                .then()
                .statusCode(OK.getStatusCode())
                .body("gateway_account_credentials.size()", is(2))
                .body("gateway_account_credentials[1].payment_provider", is("stripe"))
                .body("gateway_account_credentials[1].credentials.stripe_account_id", is("some-account-id"))
                .body("gateway_account_credentials[1].external_id", is(notNullValue(String.class)));
    }

    @Test
    public void createGatewayAccountsCredentialsValidatesRequestBusinessLogic_responseShouldBe400() {
        givenSetup()
                .body(toJson(Map.of("payment_provider", "epdq")))
                .post("/v1/api/accounts/10000/credentials")
                .then()
                .statusCode(400);
    }

    @Test
    public void patchGatewayAccountCredentialsValidRequest_responseShouldBe200() {
        Map<String, String> newCredentials = Map.of("username", "new-username",
                "password", "new-password",
                "merchant_id", "new-merchant-id");
        givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials",
                                "value", newCredentials),
                        Map.of("op", "replace",
                                "path", "last_updated_by_user_external_id",
                                "value", "a-new-user-external-id"),
                        Map.of("op", "replace",
                                "path", "state",
                                "value", "VERIFIED_WITH_LIVE_PAYMENT")
                )))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("username"))
                .body("credentials", hasKey("merchant_id"))
                .body("credentials", not(hasKey("password")))
                .body("credentials.username", is("new-username"))
                .body("credentials.merchant_id", is("new-merchant-id"))
                .body("last_updated_by_user_external_id", is("a-new-user-external-id"))
                .body("state", is("VERIFIED_WITH_LIVE_PAYMENT"))
                .body("created_date", is("2021-01-01T00:00:00.000Z"))
                .body("active_start_date", is("2021-02-01T00:00:00.000Z"))
                .body("active_end_date", is("2021-03-01T00:00:00.000Z"))
                .body("external_id", is(credentialsExternalId))
                .body("payment_provider", is("worldpay"));

        Map<String, Object> updatedGatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsById(credentialsId);
        assertThat(updatedGatewayAccountCredentials, hasEntry("last_updated_by_user_external_id", "a-new-user-external-id"));

        Map<String, String> updatedCredentials = new Gson().fromJson(((PGobject) updatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);
        assertThat(updatedCredentials, hasEntry("username", "new-username"));
        assertThat(updatedCredentials, hasEntry("password", "new-password"));
        assertThat(updatedCredentials, hasEntry("merchant_id", "new-merchant-id"));
    }

    @Test
    public void patchGatewayAccountCredentialsInvalidRequestBody_shouldReturn400() {
        Long credentialsId = (Long) databaseTestHelper.getGatewayAccountCredentialsForAccount(accountId).get(0).get("id");

        givenSetup()
                .body(toJson(singletonList(
                        Map.of("op", "replace",
                                "path", "credentials"))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(400)
                .body("message[0]", is("Field [value] is required"));
    }

    @Test
    public void patchGatewayAccountCredentialsGatewayAccountCredentialsNotFound_shouldReturn404() {
        Map<String, String> newCredentials = Map.of("username", "new-username",
                "password", "new-password",
                "merchant_id", "new-merchant-id");
        givenSetup()
                .body(toJson(singletonList(
                        Map.of("op", "replace",
                                "path", "credentials",
                                "value", newCredentials))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, 999999))
                .then()
                .statusCode(404)
                .body("message[0]", is("Gateway account credentials with id [999999] not found."));
    }

    @Test
    public void patchGatewayAccountCredentialsGatewayMerchantId() {
        givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/gateway_merchant_id",
                                "value", "abcdef123abcdef"))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("username"))
                .body("credentials", hasKey("merchant_id"))
                .body("credentials", not(hasKey("password")))
                .body("credentials", hasKey("gateway_merchant_id"))
                .body("credentials.gateway_merchant_id", is("abcdef123abcdef"));

        Map<String, Object> updatedGatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsById(credentialsId);

        Map<String, String> updatedCredentials = new Gson().fromJson(((PGobject) updatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);
        assertThat(updatedCredentials, hasEntry("username", "a-username"));
        assertThat(updatedCredentials, hasEntry("password", "a-password"));
        assertThat(updatedCredentials, hasEntry("merchant_id", "a-merchant-id"));
        assertThat(updatedCredentials, hasEntry("gateway_merchant_id", "abcdef123abcdef"));
    }

    @Test
    public void patchGatewayAccountCredentialsForGatewayMerchantIdShouldReturn400ForUnsupportedGateway() {
        DatabaseFixtures.TestAccount testAccount = addGatewayAccountAndCredential("stripe", ACTIVE, TEST);
        AddGatewayAccountCredentialsParams params = testAccount.getCredentials().get(0);

        givenSetup()
                .body(toJson(singletonList(
                        Map.of("op", "replace",
                                "path", "credentials/gateway_merchant_id",
                                "value", "abcdef123abcdef"))))
                .patch(format(PATCH_CREDENTIALS_URL, params.getGatewayAccountId(), params.getId()))
                .then()
                .statusCode(400)
                .body("message[0]", is("Gateway 'stripe' does not support digital wallets."));
    }

    @Test
    public void patchGatewayAccountCredentialsShouldNotOverWriteOtherExistingCredentials() {
        givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/gateway_merchant_id",
                                "value", "abcdef123abcdef"))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("username"))
                .body("credentials", hasKey("merchant_id"))
                .body("credentials", not(hasKey("password")))
                .body("credentials", hasKey("gateway_merchant_id"))
                .body("credentials.gateway_merchant_id", is("abcdef123abcdef"));

        Map<String, Object> newCredentials = Map.of("op", "replace",
                "path", "credentials",
                "value", Map.of("username", "new-username",
                        "password", "new-password",
                        "merchant_id", "new-merchant-id"));

        givenSetup()
                .body(toJson(List.of(newCredentials)))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("username"))
                .body("credentials.username", is("new-username"))
                .body("credentials", hasKey("merchant_id"))
                .body("credentials.merchant_id", is("new-merchant-id"))
                .body("credentials", not(hasKey("password")))
                .body("credentials", hasKey("gateway_merchant_id"))
                .body("credentials.gateway_merchant_id", is("abcdef123abcdef"));

        Map<String, Object> updatedGatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsById(credentialsId);

        Map<String, String> updatedCredentials = new Gson().fromJson(((PGobject) updatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);
        assertThat(updatedCredentials, hasEntry("username", "new-username"));
        assertThat(updatedCredentials, hasEntry("password", "new-password"));
        assertThat(updatedCredentials, hasEntry("merchant_id", "new-merchant-id"));
        assertThat(updatedCredentials, hasEntry("gateway_merchant_id", "abcdef123abcdef"));
    }

    private String getCheck3dsConfigPayloadForValidCredentials() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "issuer", VALID_ISSUER,
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", VALID_JWT_MAC_KEY));
    }

    private String getValidWorldpayCredentials() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "username", "valid-user-name",
                "password", "valid-password",
                "merchant_id", "valid-merchant-id"
        ));
    }

    private DatabaseFixtures.TestAccount addGatewayAccountAndCredential(String paymentProvider, GatewayAccountCredentialState state,
                                                                        GatewayAccountType gatewayAccountType) {
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
                .withState(state)
                .withCredentials(Map.of(
                        "merchant_id", "a-merchant-id",
                        "username", "a-username",
                        "password", "a-password"))
                .build();

        return databaseFixtures.aTestAccount().withPaymentProvider(paymentProvider)
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .withType(gatewayAccountType)
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .insert();
    }
}
