package uk.gov.pay.connector.app.agreement.resource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.Gson;
import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.postgresql.util.PGobject;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.app.ConnectorApp;
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
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class AgreementResourceIT {
    private DatabaseFixtures.TestAccount testAccount;

    private static final String CREATE_AGREEMENT_URL = "/v1/api/accounts/%s/agreements";
    
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

        testAccount = addGatewayAccountAndCredential("worldpay");
        accountId = testAccount.getAccountId();

        credentialsId = testAccount.getCredentials().get(0).getId();
        credentialsExternalId = testAccount.getCredentials().get(0).getExternalId();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort()).contentType(JSON);
    }

 




    @Ignore
    @Test
    public void createAgreement() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "reference", "1234"
        ));
        
        String s = format(CREATE_AGREEMENT_URL, testAccount.getAccountId());
        System.out.println("******* " + s);
        System.out.println("******* " + payload);
        
        givenSetup()
                .body(payload)
                .post(format(CREATE_AGREEMENT_URL, testAccount.getAccountId()))
                .then()
                .statusCode(201);
        
        
        //var result = databaseTestHelper.getWorldpay3dsFlexCredentials(accountId);
        ///assertThat(result.get("issuer"), is(VALID_ISSUER));
        //assertThat(result.get("organisational_unit_id"), is(VALID_ORG_UNIT_ID));
        //assertThat(result.get("jwt_mac_key"), is(VALID_JWT_MAC_KEY));
    }

    @Ignore
    @Test
    public void sayHello() throws JsonProcessingException {


        givenSetup()
                //.body(payload)
                .get("/hello")
                .then()
                .statusCode(200);

    }


    @Test
    public void patchGatewayAccountCredentialsInvalidRequestBody_shouldReturn400() {
        Long credentialsId = (Long) databaseTestHelper.getGatewayAccountCredentialsForAccount(accountId).get(0).get("id");
        System.out.println("****** " + format(PATCH_CREDENTIALS_URL, accountId, credentialsId));
        
        givenSetup()
                .body(toJson(singletonList(
                        Map.of("op", "replace",
                                "path", "credentials"))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(400)
                .body("message[0]", is("Field [value] is required"));
    }
    
    
    private String getCheck3dsConfigPayloadForValidCredentials() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "issuer", VALID_ISSUER,
                "organisational_unit_id", VALID_ORG_UNIT_ID,
                "jwt_mac_key", VALID_JWT_MAC_KEY));
    }

    private String getValidAgreement() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("reference", "1234"));
    }

    private String getValidWorldpayCredentials() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "username", "valid-user-name",
                "password", "valid-password",
                "merchant_id", "valid-merchant-id"
        ));
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
                        "merchant_id", "a-merchant-id",
                        "username", "a-username",
                        "password", "a-password"))
                .build();

        return databaseFixtures.aTestAccount().withPaymentProvider(paymentProvider)
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .withGatewayAccountCredentials(Collections.singletonList(credentialsParams))
                .insert();
    }
}
