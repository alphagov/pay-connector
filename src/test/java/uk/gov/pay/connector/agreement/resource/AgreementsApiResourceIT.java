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
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_CREATED;
import static java.lang.String.format;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.matchesPattern;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class AgreementsApiResourceIT {
    private static final String REFERENCE_ID = "1234";
    private static final String DESCRIPTION = "a valid description";
    private static final String USER_IDENTIFIER = "a-valid-user-identifier";
    private static final String REFERENCE_ID_TOO_LONG = new String(new char[260]).replace('\0', '1');
    private static final String REFERENCE_ID_EMPTY = "";
    private DatabaseFixtures.TestAccount testAccount;

    private static final String CREATE_AGREEMENT_URL = "/v1/api/accounts/%s/agreements";
    private static final String CANCEL_AGREEMENT_URL = "/v1/api/accounts/%s/agreements/%s/cancel";

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
    private LedgerStub ledgerStub;

    public AgreementsApiResourceIT() {
    }
    
    @Before
    public void setUp() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        wireMockServer = testContext.getWireMockServer();
        worldpayMockClient = new WorldpayMockClient(wireMockServer);
        ledgerStub = new LedgerStub(wireMockServer);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);

        testAccount = createTestAccount("worldpay");
        accountId = testAccount.getAccountId();

        credentialsId = testAccount.getCredentials().get(0).getId();
        credentialsExternalId = testAccount.getCredentials().get(0).getExternalId();
        ledgerStub.acceptPostEvent();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort()).contentType(JSON);
    }
    
    @Test
    public void shouldCreateAgreement() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "reference", REFERENCE_ID,
                "description", DESCRIPTION,
                "user_identifier", USER_IDENTIFIER
        ));

        ValidatableResponse o = givenSetup()
                .body(payload)
                .post(format(CREATE_AGREEMENT_URL, accountId))

                .then()
                .statusCode(SC_CREATED)
                .body("reference", equalTo(REFERENCE_ID))
                .body("service_id", equalTo("valid-external-service-id"))
                .body("agreement_id", notNullValue())
                .body("description", equalTo(DESCRIPTION))
                .body("user_identifier", equalTo(USER_IDENTIFIER))
                .body("created_date", notNullValue())
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("created_date", isWithin(10, SECONDS));
    }

    @Test
    public void shouldReturn422WhenReferenceIdTooLong() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "reference", REFERENCE_ID_TOO_LONG,
                "description", DESCRIPTION
        ));

        givenSetup()
                .body(payload)
                .post(format(CREATE_AGREEMENT_URL, accountId))
                .then()
                .statusCode(SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void shouldReturn422WhenReferenceIdEmpty() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "reference", REFERENCE_ID_EMPTY,
                "description", DESCRIPTION
        ));

        givenSetup()
                .body(payload)
                .post(format(CREATE_AGREEMENT_URL, accountId))
                .then()
                .statusCode(SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void shouldReturn422WhenDescriptionEmpty() throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "reference", REFERENCE_ID
        ));

        givenSetup()
                .body(payload)
                .post(format(CREATE_AGREEMENT_URL, accountId))
                .then()
                .statusCode(SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void shouldReturn204AndCancelAgreement() {
        var agreementId = "an-external-id";
        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(nextLong())
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        databaseTestHelper.addPaymentInstrument(paymentInstrumentParams);
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(String.valueOf(accountId))
                .withExternalAgreementId(agreementId)
                .withPaymentInstrumentId(paymentInstrumentParams.getPaymentInstrumentId())
                .build();
        databaseTestHelper.addAgreement(agreementParams);

        givenSetup()
                .post(format(CANCEL_AGREEMENT_URL, accountId, agreementId))
                .then()
                .statusCode(NO_CONTENT_204);
        var agreementMap = databaseTestHelper.getPaymentInstrument(paymentInstrumentParams.getPaymentInstrumentId());
        assertThat(agreementMap.get("status"), is("CANCELLED"));
    }

    private DatabaseFixtures.TestAccount createTestAccount(String paymentProvider) {
        long accountId = nextLong(2, 10000);

        return databaseFixtures.aTestAccount().withPaymentProvider(paymentProvider)
                .withIntegrationVersion3ds(2)
                .withAccountId(accountId)
                .insert();
    }
}
