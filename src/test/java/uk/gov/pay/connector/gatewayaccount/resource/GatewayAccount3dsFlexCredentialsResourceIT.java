package uk.gov.pay.connector.gatewayaccount.resource;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.specification.RequestSpecification;
import junitparams.Parameters;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.commons.model.ErrorIdentifier;
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
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccount3dsFlexCredentialsResourceIT {
    private DatabaseFixtures.TestAccount testAccount;

    private static final String ACCOUNTS_API_URL = "/v1/api/accounts/%s/3ds-flex-credentials";
    private static final String VALIDATE_3DS_FLEX_CREDENTIALS_URL = "/v1/api/accounts/%s/worldpay/check-3ds-flex-config";
    
    @DropwizardTestContext
    protected TestContext testContext;
    protected DatabaseTestHelper databaseTestHelper;
    private DatabaseFixtures databaseFixtures;
    private Long accountId;
    private ObjectMapper objectMapper = new ObjectMapper();

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);

    @Before
    public void setUp() {
        accountId = nextLong(2, 10000);
        databaseTestHelper = testContext.getDatabaseTestHelper();
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        testAccount = databaseFixtures.aTestAccount().withPaymentProvider("worldpay")
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .insert();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort()).contentType(JSON);
    }
    
    @Test
    public void validate_valid_3ds_flex_credentials() throws Exception {
        wireMockRule.stubFor(post("/shopper/3ds/ddc.html").willReturn(ok()));

        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200)
                .body("result", is("valid"));
    }

    @Test
    public void validate_invalid_3ds_flex_credentials() throws Exception {
        wireMockRule.stubFor(post("/shopper/3ds/ddc.html").willReturn(badRequest()));

        var invalidIssuer = "54i0917n10va4428b69l5id0";
        var invalidOrgUnitId = "57002a087a0c4849895ab8a2";
        var invalidJwtMacKey = "4inva5l2-0133-4i82-d0e5-2024dbeddaa9";
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
        wireMockRule.stubFor(post("/shopper/3ds/ddc.html").willReturn(serverError()));

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
        var validIssuer = "54i0917n10va4428b69l5id0";
        var validJwtMacKey = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9";
        var payload = objectMapper.writeValueAsString(Map.of("issuer", validIssuer,
                "organisational_unit_id", "incorrectFormat",
                "jwt_mac_key", validJwtMacKey));

        givenSetup()
                .body(payload)
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("error_identifier", is(ErrorIdentifier.GENERIC.name()))
                .body("message[0]", is("Field [organisational_unit_id] must be 24 lower-case hexadecimal characters"));
    }

    @Test
    public void should_return_422_if_organisation_unit_id_is_null() throws Exception {
        var validIssuer = "54i0917n10va4428b69l5id0";
        var validJwtMacKey = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9";
        var jsonFields = new HashMap<String, String>();
        jsonFields.put("issuer", validIssuer);
        jsonFields.put("organisational_unit_id", null);
        jsonFields.put("jwt_mac_key", validJwtMacKey);
        var payload = objectMapper.writeValueAsString(jsonFields);

        givenSetup()
                .body(payload)
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .body("error_identifier", is(ErrorIdentifier.GENERIC.name()))
                .body("message[0]", is("Field [organisational_unit_id] must be 24 lower-case hexadecimal characters"));
    }

    @Test
    public void setWorldpay3dsFlexCredentialsWhenThereAreNonExisting() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "issuer", "testissuer",
                "organisational_unit_id", "57992a087a0c4849895ab8a2",
                "jwt_mac_key", "hihihihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200);
        var result = databaseTestHelper.getWorldpay3dsFlexCredentials(accountId);
        assertThat(result.get("issuer"), is("testissuer"));
        assertThat(result.get("organisational_unit_id"), is("57992a087a0c4849895ab8a2"));
        assertThat(result.get("jwt_mac_key"), is("hihihihihi"));
    }

    @Test
    public void overrideSetWorldpay3dsCredentials() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "issuer", "testissuer",
                "organisational_unit_id", "57992a087a0c4849895ab8a2",
                "jwt_mac_key", "hihihihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200);
        payload = objectMapper.writeValueAsString(Map.of(
                "issuer", "updated_issuer",
                "organisational_unit_id", "44992a087a0c4849895cc9a3",
                "jwt_mac_key", "updated_jwt_mac_key"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200);
        var result = databaseTestHelper.getWorldpay3dsFlexCredentials(accountId);
        assertThat(result.get("issuer"), is("updated_issuer"));
        assertThat(result.get("organisational_unit_id"), is("44992a087a0c4849895cc9a3"));
        assertThat(result.get("jwt_mac_key"), is("updated_jwt_mac_key"));
    }

    @Test
    @Parameters({"issuer", "jwt_mac_key"})
    public void missingFieldsReturnCorrectError(String field) throws JsonProcessingException {
        Map<String, String> jsonFields = new HashMap<>();
        jsonFields.put("issuer", "testissuer");
        jsonFields.put("jwt_mac_key", "hihihihihi");
        jsonFields.put("organisational_unit_id", "57992a087a0c4849895ab8a2");
        jsonFields.remove(field);

        givenSetup()
                .body(objectMapper.writeValueAsString(jsonFields))
                .post(format(ACCOUNTS_API_URL, testAccount.getAccountId()))
                .then()
                .statusCode(422)
                .body("message[0]", is(format("Field [%s] cannot be null", field)));
    }

    @Test
    public void nonExistentGatewayAccountReturns404() throws JsonProcessingException {
        Long fakeAccountId = RandomUtils.nextLong();
        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(ACCOUNTS_API_URL, fakeAccountId))
                .then()
                .statusCode(404)
                .body("message[0]", is("Not a Worldpay gateway account"));
    }

    @Test
    public void nonWorldpayGatewayAccountReturns404() throws JsonProcessingException {
        long fakeAccountId = RandomUtils.nextLong();
        databaseFixtures.aTestAccount().withPaymentProvider("smartpay")
                .withIntegrationVersion3ds(2)
                .withAccountId(fakeAccountId)
                .insert();
        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(ACCOUNTS_API_URL, fakeAccountId))
                .then()
                .statusCode(404)
                .body("message[0]", is("Not a Worldpay gateway account"));
    }

    private String getCheck3dsConfigPayloadForValidCredentials() throws JsonProcessingException {
        var validIssuer = "53f0917f101a4428b69d5fb0";
        var validOrgUnitId = "57992a087a0c4849895ab8a2";
        var validJwtMacKey = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9";
        return objectMapper.writeValueAsString(Map.of(
                "issuer", validIssuer,
                "organisational_unit_id", validOrgUnitId,
                "jwt_mac_key", validJwtMacKey));
    }
}
