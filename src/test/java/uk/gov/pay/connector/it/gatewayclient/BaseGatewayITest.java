package uk.gov.pay.connector.it.gatewayclient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.testing.port.PortFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

@RunWith(MockitoJUnitRunner.class)
abstract public class BaseGatewayITest {
    private static final String TRANSACTION_ID = String.valueOf(nextLong());
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of(
                    CREDENTIALS_MERCHANT_ID, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password"
            );
    private static final String PAYMENT_PROVIDER = "smartpay";

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    protected int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    public DatabaseFixtures.TestCharge createTestCharge(DatabaseTestHelper databaseTestHelper) {
        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withPaymentProvider(PAYMENT_PROVIDER)
                .withCredentials(CREDENTIALS);

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(ChargeStatus.CAPTURE_APPROVED)
                .withTransactionId(TRANSACTION_ID);

        testAccount.insert();
        return testCharge.insert();
    }

    public GatewayStub setupGatewayStub() {
        return new GatewayStub(TRANSACTION_ID);
    }

    public void setupLoggerMockAppender() {
        Logger root = (Logger) LoggerFactory.getLogger(GatewayClient.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    public void assertLastGatewayClientLoggingEventContains(String loggingEvent) {
        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        assertThat(logStatement.stream().anyMatch(x -> x.getFormattedMessage().contains(loggingEvent)), is(true));
    }
}
