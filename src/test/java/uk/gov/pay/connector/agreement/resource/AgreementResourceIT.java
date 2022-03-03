package uk.gov.pay.connector.agreement.resource;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static java.lang.String.format;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class AgreementResourceIT {
    private static final String REFERENCE_ID = "1234";
    private DatabaseFixtures.TestAccount testAccount;

    private static final String CREATE_AGREEMENT_URL = "/v1/api/accounts/%s/agreements";

    @DropwizardTestContext
    private TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;
    private WireMockServer wireMockServer;
    private WorldpayMockClient worldpayMockClient;
    private DatabaseFixtures databaseFixtures;
    private Long credentialsId;
    private String credentialsExternalId;
    private Long accountId;
    private ObjectMapper objectMapper = new ObjectMapper();
    
    public AgreementResourceIT() {
    }
    
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
    
    @Test
    public void testShouldCreateAgreement() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "reference", REFERENCE_ID
        ));

        ValidatableResponse o = givenSetup()
                .body(payload)
                .post(format(CREATE_AGREEMENT_URL, accountId))

                .then()
                .statusCode(200)
                .body("reference", equalTo(REFERENCE_ID))
                .body("service_id", equalTo("valid-external-service-id"))
                .body("agreement_id", notNullValue())
                .body("created_date", notNullValue());
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
