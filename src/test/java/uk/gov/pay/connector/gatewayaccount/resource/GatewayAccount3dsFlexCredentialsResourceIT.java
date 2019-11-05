package uk.gov.pay.connector.gatewayaccount.resource;

import io.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccount3dsFlexCredentialsResourceIT {
    private DatabaseFixtures.TestAccount testAccount;

    private static final String ACCOUNTS_API_URL = "/v1/api/accounts/%s/3ds-flex-credentials";

    @DropwizardTestContext
    protected TestContext testContext;
    protected DatabaseTestHelper databaseTestHelper;
    private DatabaseFixtures databaseFixtures;
    private Long accountId = 1L;

    @Before
    public void setUp() {
        accountId = RandomUtils.nextLong();
        databaseTestHelper = testContext.getDatabaseTestHelper();
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        testAccount = databaseFixtures.aTestAccount().withPaymentProvider("worldpay")
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .insert();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort())
                .contentType(JSON);
    }

    @Test
    public void setWorldpay3dsFlexCredentialsWhenThereAreNonExisting() throws JsonProcessingException {
         String payload = new ObjectMapper().writeValueAsString(Map.of(
                "issuer", "testissuer",
                "organisational_unit_id", "hihihi",
                "jwt_mac_key", "hihihihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,testAccount.getAccountId()))
                .then()
                .statusCode(200);
        var result = databaseTestHelper.getWorldpay3dsFlexCredentials(accountId); 
        assertThat(result.get("issuer"), is("testissuer"));
        assertThat(result.get("organisational_unit_id"), is("hihihi"));
        assertThat(result.get("jwt_mac_key"), is("hihihihihi"));
    }

    @Test
    public void overrideSetWorldpay3dsCredentials() throws JsonProcessingException {
        String payload =new ObjectMapper().writeValueAsString(Map.of(
                "issuer", "testissuer",
                "organisational_unit_id", "hihihi",
                "jwt_mac_key", "hihihihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,testAccount.getAccountId()))
                .then()
                .statusCode(200);
        payload = new ObjectMapper().writeValueAsString(Map.of(
                "issuer", "updated_issuer",
                "organisational_unit_id", "updated_organisational_unit_id",
                "jwt_mac_key", "updated_jwt_mac_key"
        ));
        givenSetup()
                .body(payload) 
                .post(format(ACCOUNTS_API_URL,testAccount.getAccountId()))
                .then()
                .statusCode(200);
        var result = databaseTestHelper.getWorldpay3dsFlexCredentials(accountId);
        assertThat(result.get("issuer"), is("updated_issuer"));
        assertThat(result.get("organisational_unit_id"), is("updated_organisational_unit_id"));
        assertThat(result.get("jwt_mac_key"), is("updated_jwt_mac_key"));
    }

    @Test
    public void missingFieldsReturnCorrectError() throws JsonProcessingException {
        String payload = new ObjectMapper().writeValueAsString(Map.of(
                "issuer", "testissuer",
                "jwt_mac_key", "hihihihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,testAccount.getAccountId()))
                .then()
                .statusCode(422)
                .body("message[0]", is("Field [organisational_unit_id] cannot be null"));
        payload = new ObjectMapper().writeValueAsString(Map.of(
                "issuer", "testissuer",
                "organisational_unit_id", "hihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,testAccount.getAccountId()))
                .then()
                .statusCode(422)
                .body("message[0]", is("Field [jwt_mac_key] cannot be null"));
        payload = new ObjectMapper().writeValueAsString(Map.of(
                "organisational_unit_id", "hihihi",
                "jwt_mac_key", "hihihihihi"
        ));        
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,testAccount.getAccountId()))
                .then()
                .statusCode(422)
                .body("message[0]", is("Field [issuer] cannot be null"));
        payload = new ObjectMapper().writeValueAsString(Map.of(
                "organisational_unit_id", "hihihi"
        ));        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,testAccount.getAccountId()))
                .then()
                .statusCode(422)
                .body("message", hasItem("Field [jwt_mac_key] cannot be null"))
                .body("message", hasItem("Field [issuer] cannot be null"));
    }

    @Test
    public void postRequestWithNullParameter() throws JsonProcessingException {
        HashMap<String, String> payloadMap = new HashMap<>();
        payloadMap.put("issuer", "testissuer");
        payloadMap.put("organisational_unit_id", null);
        payloadMap.put("jwt_mac_key", "hihihihihi");
        String payload = new ObjectMapper().writeValueAsString(payloadMap);
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,accountId))
                .then()
                .statusCode(422)
                .body("message[0]", is("Field [organisational_unit_id] cannot be null"));
    }

    @Test
    public void nonExistentGatewayAccountReturns404() throws JsonProcessingException {
        Long fakeAccountId = RandomUtils.nextLong();
        String payload = new ObjectMapper().writeValueAsString(Map.of(
                "issuer", "testissuer",
                "organisational_unit_id", "hihihi",
                "jwt_mac_key", "hihihihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,fakeAccountId))
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
        String payload = new ObjectMapper().writeValueAsString(Map.of(
                "issuer", "testissuer",
                "organisational_unit_id", "hihihi",
                "jwt_mac_key", "hihihihihi"
        ));
        givenSetup()
                .body(payload)
                .post(format(ACCOUNTS_API_URL,fakeAccountId))
                .then()
                .statusCode(404)
                .body("message[0]", is("Not a Worldpay gateway account"));
    }
}
