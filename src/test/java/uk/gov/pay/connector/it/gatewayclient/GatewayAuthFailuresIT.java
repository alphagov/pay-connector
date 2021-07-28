package uk.gov.pay.connector.it.gatewayclient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import org.hamcrest.core.Is;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.config.ConnectionConfig.connectionConfig;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAuthFailuresIT {

    private static final String TRANSACTION_ID = "7914440428682669";
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of("username", "a-user", "password", "a-password", "merchant_id", "aMerchantCode");
    private static final String PAYMENT_PROVIDER = "smartpay";

    @DropwizardTestContext
    public TestContext testContext;
    
    private WireMockServer wireMockServer;
    
    private GatewayStub gatewayStub;
    private DatabaseTestHelper databaseTestHelper;
    private DatabaseFixtures.TestCharge chargeTestRecord;
    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
    private AddGatewayAccountCredentialsParams credentialParams;

    @Before
    public void setup() {
        gatewayStub = new GatewayStub(TRANSACTION_ID);

        databaseTestHelper = testContext.getDatabaseTestHelper();
        wireMockServer = testContext.getWireMockServer();

        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withPaymentProvider(PAYMENT_PROVIDER)
                .withCredentials(CREDENTIALS);

        credentialParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(PAYMENT_PROVIDER)
                .withState(ACTIVE)
                .withCredentials(CREDENTIALS)
                .withGatewayAccountId(testAccount.getAccountId())
                .build();

        testAccount.withGatewayAccountCredentials(List.of(credentialParams));

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(testAccount)
                .withPaymentProvider(PAYMENT_PROVIDER)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .withGatewayCredentialId(credentialParams.getId())
                .withTransactionId(TRANSACTION_ID);

        testAccount.insert();
        this.chargeTestRecord = testCharge.insert();

        Logger root = (Logger) LoggerFactory.getLogger(GatewayClient.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @After
    public void teardown() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void shouldFailAuthWhenUnexpectedHttpStatusCodeFromGateway() {
        gatewayStub.respondWithUnexpectedResponseCodeWhenCardAuth(wireMockServer);

        String errorMessage = "Non-success HTTP status code 999 from gateway";
        String cardAuthUrl = "/v1/frontend/charges/{chargeId}/cards".replace("{chargeId}", chargeTestRecord.getExternalChargeId());
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();

        given().config(RestAssured.config().connectionConfig(connectionConfig().closeIdleConnectionsAfterEachResponse()))
                .port(testContext.getPort())
                .contentType(JSON)
                .when()
                .body(authCardDetails)
                .post(cardAuthUrl)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", contains(errorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertThatLastGatewayClientLoggingEventIs(
                String.format("Gateway returned unexpected status code: 999, for gateway url=http://localhost:%s/pal/servlet/soap/Payment with type test with order request type authorise", wireMockServer.getOptions().portNumber()));
        assertThat(databaseTestHelper.getChargeStatus(chargeTestRecord.getChargeId()), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    private void assertThatLastGatewayClientLoggingEventIs(String loggingEvent) {
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.get(logStatement.size() - 1).getFormattedMessage(), Is.is(loggingEvent));
    }
}
