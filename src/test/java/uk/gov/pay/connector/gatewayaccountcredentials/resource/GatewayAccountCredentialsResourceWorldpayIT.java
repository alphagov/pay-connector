package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.Gson;
import io.restassured.specification.RequestSpecification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.postgresql.util.PGobject;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
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

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountCredentialsResourceWorldpayIT {
    private DatabaseFixtures.TestAccount testAccount;

    private static final String PATCH_CREDENTIALS_URL = "/v1/api/accounts/%s/credentials/%s";
    private static final String VALIDATE_WORLDPAY_CREDENTIALS_URL = "/v1/api/accounts/%s/worldpay/check-credentials";

    @DropwizardTestContext
    protected TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;
    private WireMockServer wireMockServer;
    private WorldpayMockClient worldpayMockClient;
    private DatabaseFixtures databaseFixtures;
    private Long credentialsId;
    private Long accountId;

    @Before
    public void setUp() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        wireMockServer = testContext.getWireMockServer();
        worldpayMockClient = new WorldpayMockClient(wireMockServer);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);

        testAccount = addGatewayAccountAndCredential("worldpay", ACTIVE, TEST);
        accountId = testAccount.getAccountId();

        credentialsId = testAccount.getCredentials().get(0).getId();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort()).contentType(JSON);
    }

    @Test
    public void existingOneOffCredentialsCanBeReplaced() {
        givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/worldpay/one_off_customer_initiated",
                                "value", Map.of("merchant_code", "new-merchant-code",
                                        "username", "new-username",
                                        "password", "new-password")))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("one_off_customer_initiated"))
                .body("credentials.one_off_customer_initiated", hasEntry("merchant_code", "new-merchant-code"))
                .body("credentials.one_off_customer_initiated", hasEntry("username", "new-username"))
                .body("credentials.one_off_customer_initiated", not(hasKey("password")));

        Map<String, Object> updatedGatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsById(credentialsId);

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedCredentials = new Gson().fromJson(((PGobject) updatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, String> updatedOneOffCustomerInitiated = (Map<String, String>) updatedCredentials.get("one_off_customer_initiated");
        assertThat(updatedOneOffCustomerInitiated, hasEntry("merchant_code", "new-merchant-code"));
        assertThat(updatedOneOffCustomerInitiated, hasEntry("username", "new-username"));
        assertThat(updatedOneOffCustomerInitiated, hasEntry("password", "new-password"));

        givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/worldpay/one_off_customer_initiated",
                                "value", Map.of("merchant_code", "newer-merchant-code",
                                        "username", "newer-username",
                                        "password", "newer-password")))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("one_off_customer_initiated"))
                .body("credentials.one_off_customer_initiated", hasEntry("merchant_code", "newer-merchant-code"))
                .body("credentials.one_off_customer_initiated", hasEntry("username", "newer-username"))
                .body("credentials.one_off_customer_initiated", not(hasKey("password")));

        Map<String, Object> moreUpdatedGatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsById(credentialsId);

        @SuppressWarnings("unchecked")
        Map<String, Object> moreUpdatedCredentials = new Gson().fromJson(((PGobject) moreUpdatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, String> moreUpdatedOneOffCustomerInitiated = (Map<String, String>) moreUpdatedCredentials.get("one_off_customer_initiated");
        assertThat(moreUpdatedOneOffCustomerInitiated, hasEntry("merchant_code", "newer-merchant-code"));
        assertThat(moreUpdatedOneOffCustomerInitiated, hasEntry("username", "newer-username"));
        assertThat(moreUpdatedOneOffCustomerInitiated, hasEntry("password", "newer-password"));
    }

    @Test
    public void addingRecurringCredentialsCanBeDoneInStages() {
        givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/worldpay/recurring_customer_initiated",
                                "value", Map.of("merchant_code", "new-recurring-cit-merchant-code",
                                        "username", "new-recurring-cit-username",
                                        "password", "new-recurring-cit-password")))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("recurring_customer_initiated"))
                .body("credentials.recurring_customer_initiated", hasEntry("merchant_code", "new-recurring-cit-merchant-code"))
                .body("credentials.recurring_customer_initiated", hasEntry("username", "new-recurring-cit-username"))
                .body("credentials.recurring_customer_initiated", not(hasKey("password")));

        Map<String, Object> updatedGatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsById(credentialsId);

        @SuppressWarnings("unchecked")
        Map<String, Object> updatedCredentials = new Gson().fromJson(((PGobject) updatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, String> updatedOneOffCustomerInitiated = (Map<String, String>) updatedCredentials.get("recurring_customer_initiated");
        assertThat(updatedOneOffCustomerInitiated, hasEntry("merchant_code", "new-recurring-cit-merchant-code"));
        assertThat(updatedOneOffCustomerInitiated, hasEntry("username", "new-recurring-cit-username"));
        assertThat(updatedOneOffCustomerInitiated, hasEntry("password", "new-recurring-cit-password"));

        assertThat(updatedCredentials, not(hasKey("recurring_merchant_initiated")));

        givenSetup()
                .body(toJson(List.of(
                        Map.of("op", "replace",
                                "path", "credentials/worldpay/recurring_merchant_initiated",
                                "value", Map.of("merchant_code", "new-recurring-mit-merchant-code",
                                        "username", "new-recurring-mit-username",
                                        "password", "new-recurring-mit-password")))))
                .patch(format(PATCH_CREDENTIALS_URL, accountId, credentialsId))
                .then()
                .statusCode(200)
                .body("$", hasKey("credentials"))
                .body("credentials", hasKey("recurring_merchant_initiated"))
                .body("credentials.recurring_merchant_initiated", hasEntry("merchant_code", "new-recurring-mit-merchant-code"))
                .body("credentials.recurring_merchant_initiated", hasEntry("username", "new-recurring-mit-username"))
                .body("credentials.recurring_merchant_initiated", not(hasKey("password")));

        Map<String, Object> moreUpdatedGatewayAccountCredentials = databaseTestHelper.getGatewayAccountCredentialsById(credentialsId);

        @SuppressWarnings("unchecked")
        Map<String, Object> moreUpdatedCredentials = new Gson().fromJson(((PGobject) moreUpdatedGatewayAccountCredentials.get("credentials")).getValue(), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, String> moreUpdatedOneOffCustomerInitiated = (Map<String, String>) moreUpdatedCredentials.get("recurring_customer_initiated");
        assertThat(moreUpdatedOneOffCustomerInitiated, hasEntry("merchant_code", "new-recurring-cit-merchant-code"));
        assertThat(moreUpdatedOneOffCustomerInitiated, hasEntry("username", "new-recurring-cit-username"));
        assertThat(moreUpdatedOneOffCustomerInitiated, hasEntry("password", "new-recurring-cit-password"));

        @SuppressWarnings("unchecked")
        Map<String, String> moreUpdatedOneOffMerchantInitiated = (Map<String, String>) moreUpdatedCredentials.get("recurring_merchant_initiated");
        assertThat(moreUpdatedOneOffMerchantInitiated, hasEntry("merchant_code", "new-recurring-mit-merchant-code"));
        assertThat(moreUpdatedOneOffMerchantInitiated, hasEntry("username", "new-recurring-mit-username"));
        assertThat(moreUpdatedOneOffMerchantInitiated, hasEntry("password", "new-recurring-mit-password"));
    }

    @Test
    public void checkWorldpayCredentials_returns500WhenWorldpayReturnsUnexpectedResponse() throws JsonProcessingException {
        worldpayMockClient.mockCredentialsValidationUnexpectedResponse();

        long accountId = nextLong(2, 10000);
        databaseFixtures.aTestAccount().withAccountId(accountId).withPaymentProvider("worldpay").insert();
        givenSetup()
                .body(Map.of(
                        "username", "valid-user-name",
                        "password", "valid-password",
                        "merchant_id", "valid-merchant-id"
                ))
                .post(format(VALIDATE_WORLDPAY_CREDENTIALS_URL, accountId))
                .then()
                .statusCode(500)
                .body("message[0]", is("Worldpay returned an unexpected response when validating credentials"));
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
