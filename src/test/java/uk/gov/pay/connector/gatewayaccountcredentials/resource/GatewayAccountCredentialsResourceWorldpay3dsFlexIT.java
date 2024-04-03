package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;

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
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

public class GatewayAccountCredentialsResourceWorldpay3dsFlexIT {
    @RegisterExtension
    static ITestBaseExtension app = new ITestBaseExtension("worldpay");
    private DatabaseFixtures.TestAccount testAccount;

    private static final String UPDATE_3DS_FLEX_CREDENTIALS_URL = "/v1/api/accounts/%s/3ds-flex-credentials";
    private static final String VALIDATE_3DS_FLEX_CREDENTIALS_URL = "/v1/api/accounts/%s/worldpay/check-3ds-flex-config";

    public static final String VALID_ISSUER = "53f0917f101a4428b69d5fb0"; // pragma: allowlist secret
    public static final String VALID_ORG_UNIT_ID = "57992a087a0c4849895ab8a2"; // pragma: allowlist secret
    public static final String VALID_JWT_MAC_KEY = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9"; // pragma: allowlist secret

    private Long accountId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        testAccount = addGatewayAccountAndCredential("worldpay", ACTIVE, TEST);
        accountId = testAccount.getAccountId();
    }

    protected RequestSpecification givenSetup() {
        return given().port(app.getLocalPort()).contentType(JSON);
    }

    @Test
    public void validate_valid_3ds_flex_credentials() throws Exception {
        app.getWorldpayWireMockServer().stubFor(post("/shopper/3ds/ddc.html").willReturn(ok()));

        givenSetup()
                .body(getCheck3dsConfigPayloadForValidCredentials())
                .post(format(VALIDATE_3DS_FLEX_CREDENTIALS_URL, testAccount.getAccountId()))
                .then()
                .statusCode(200)
                .body("result", is("valid"));
    }

    @Test
    public void validate_invalid_3ds_flex_credentials() throws Exception {
        app.getWorldpayWireMockServer().stubFor(post("/shopper/3ds/ddc.html").willReturn(badRequest()));

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
        app.getWorldpayWireMockServer().stubFor(post("/shopper/3ds/ddc.html").willReturn(serverError()));

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
        var result = app.getDatabaseTestHelper().getWorldpay3dsFlexCredentials(accountId);
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

        var result = app.getDatabaseTestHelper().getWorldpay3dsFlexCredentials(testAccount.getAccountId());
        assertThat(result.get("issuer"), is(VALID_ISSUER));
        assertThat(result.get("organisational_unit_id"), is(VALID_ORG_UNIT_ID));
        assertThat(result.get("jwt_mac_key"), is(VALID_JWT_MAC_KEY));

        List<Map<String, Object>> gatewayAccountCredentials = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(testAccount.getAccountId());
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
        var result = app.getDatabaseTestHelper().getWorldpay3dsFlexCredentials(accountId);
        assertThat(result.get("issuer"), is(newIssuer));
        assertThat(result.get("organisational_unit_id"), is(newOrgUnitId));
        assertThat(result.get("jwt_mac_key"), is(updatedJwtMacKey));
    }


    private String getCheck3dsConfigPayloadForValidCredentials() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "issuer", VALID_ISSUER,
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", VALID_JWT_MAC_KEY));
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
                        ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                CREDENTIALS_MERCHANT_CODE, "a-merchant-code",
                                CREDENTIALS_USERNAME, "a-username",
                                CREDENTIALS_PASSWORD, "a-password")))
                .build();

        return app.getDatabaseFixtures().aTestAccount().withPaymentProvider(paymentProvider)
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .withType(gatewayAccountType)
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .insert();
    }

}
