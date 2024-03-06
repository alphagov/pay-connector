package uk.gov.pay.connector.it.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.restassured.specification.RequestSpecification;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import uk.gov.pay.connector.rules.AppWithPostgresAndSqsRule;
import uk.gov.pay.connector.rules.CardidStub;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

public class IntegrationTest {

    @ClassRule
    public static final AppWithPostgresAndSqsRule connectorApp = new AppWithPostgresAndSqsRule();

    protected static WorldpayMockClient worldpayMockClient;
    protected static StripeMockClient stripeMockClient;
    protected static LedgerStub ledgerStub;
    protected static DatabaseTestHelper databaseTestHelper;
    protected static WireMockServer wireMockServer;
    protected static Injector injector = Guice.createInjector();
    protected static CardidStub cardidStub;
    protected static ObjectMapper mapper;

    @BeforeClass
    public static void setUpIntegrationTest() {
        wireMockServer = connectorApp.getWireMockServer();
        worldpayMockClient = new WorldpayMockClient(wireMockServer);
        stripeMockClient = new StripeMockClient(wireMockServer);
        ledgerStub = new LedgerStub(wireMockServer);
        cardidStub = new CardidStub(wireMockServer);
        databaseTestHelper = connectorApp.getDatabaseTestHelper();
        mapper = new ObjectMapper();
    }

    RequestSpecification givenSetup() {
        return given().port(connectorApp.getLocalPort())
                .contentType(JSON);
    }
}
