package uk.gov.pay.connector.it.gatewayclient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.util.PortFactory;

import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.ConnectionConfig.connectionConfig;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.util.AuthUtils.aValidAuthorisationDetails;

@RunWith(MockitoJUnitRunner.class)
public class GatewayAuthFailuresITest {
    private static final String TRANSACTION_ID = "7914440428682669";
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of("username", "a-user", "password", "a-password", "merchant_id", "aMerchantCode");
    private static final String PAYMENT_PROVIDER = "smartpay";

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment")
    );

    private GatewayStub gatewayStub;

    private DatabaseFixtures.TestCharge chargeTestRecord;

    @Before
    public void setup() {
        gatewayStub = new GatewayStub(TRANSACTION_ID);

        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withPaymentProvider(PAYMENT_PROVIDER)
                .withCredentials(CREDENTIALS);

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(ChargeStatus.ENTERING_CARD_DETAILS)
                .withTransactionId(TRANSACTION_ID);

        testAccount.insert();
        this.chargeTestRecord = testCharge.insert();

        Logger root = (Logger) LoggerFactory.getLogger(GatewayClient.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void shouldFailAuthWhenUnexpectedHttpStatusCodeFromGateway() throws Exception {
        gatewayStub.respondWithUnexpectedResponseCodeWhenCardAuth();

        String errorMessage = "Unexpected HTTP status code 999 from gateway";
        String cardAuthUrl = "/v1/frontend/charges/{chargeId}/cards".replace("{chargeId}", chargeTestRecord.getExternalChargeId());

        given().config(RestAssured.config().connectionConfig(connectionConfig().closeIdleConnectionsAfterEachResponse()))
                .port(app.getLocalPort())
                .contentType(JSON)
                .when()
                .body(aValidAuthorisationDetails())
                .post(cardAuthUrl)
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertThatLastGatewayClientLoggingEventIs(
                String.format("Gateway returned unexpected status code: 999, for gateway url=http://localhost:%s/pal/servlet/soap/Payment with type test", port));
        assertThat(app.getDatabaseTestHelper().getChargeStatus(chargeTestRecord.getChargeId()), is(AUTHORISATION_UNEXPECTED_ERROR.getValue()));
    }

    private void assertThatLastGatewayClientLoggingEventIs(String loggingEvent) {
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        Assert.assertThat(logStatement.get(logStatement.size() - 1).getFormattedMessage(), Is.is(loggingEvent));
    }
}
