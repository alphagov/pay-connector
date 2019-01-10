package uk.gov.pay.connector.it.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml",
        configOverrides = {
                @ConfigOverride(key = "logging.level", value = "INFO"),
                @ConfigOverride(key = "captureProcessConfig.schedulerThreads", value = "2"),
                @ConfigOverride(key = "captureProcessConfig.schedulerInitialDelayInSeconds", value = "10"), // delay so DB migrations can be complete before capture process
                @ConfigOverride(key = "captureProcessConfig.schedulerRandomIntervalMinimumInSeconds", value = "5"),
                @ConfigOverride(key = "captureProcessConfig.schedulerRandomIntervalMaximumInSeconds", value = "5")
        }
)
public class CaptureProcessSchedulerITest {

    private static final String PAYMENT_PROVIDER = "worldpay";

    private static final String TRANSACTION_ID = "7914440428682669";
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of(
                    CREDENTIALS_MERCHANT_ID, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password",
                    CREDENTIALS_SHA_IN_PASSPHRASE, "sha-passphraser"
            );

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    @DropwizardTestContext
    protected TestContext testContext;

    protected DatabaseTestHelper databaseTestHelper;

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);
    
    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        Logger root = (Logger) LoggerFactory.getLogger(CardCaptureProcess.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void schedulerShouldStartMultipleThreadsAsPerConfig_AndCaptureCharge() throws InterruptedException {

        new WorldpayMockClient().mockCaptureSuccess();
        DatabaseFixtures.TestCharge testCharge = ChargeUtils.createTestCharge(databaseTestHelper, PAYMENT_PROVIDER, CAPTURE_APPROVED,
                CREDENTIALS, TRANSACTION_ID);
        
        Thread.sleep(10000L); // Wait for Capture process to finish capture

        // Expected below : For 2 scheduler threads with a charge in CAPTURE_APPROVED state
        // Thread 1: Capturing: 1 of 1 charges ,  Capturing [1 of 1] [chargeId=...] ,  Capture complete ...
        // Thread 2: Capture complete ....
        verify(mockAppender, times(4)).doAppend(loggingEventArgumentCaptor.capture());
        assertThatTwoThreadsAreCompletingCaptureProcess(loggingEventArgumentCaptor);

        assertThat(databaseTestHelper.getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_SUBMITTED.getValue()));
    }

    private void assertThatTwoThreadsAreCompletingCaptureProcess(ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor) {
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();

        int noOfCaptureCompletes = 0;
        for (LoggingEvent loggingEvent : loggingEvents) {
            if (loggingEvent.getFormattedMessage().contains("Capture complete")) {
                noOfCaptureCompletes++;
            }
        }
        assertEquals(2, noOfCaptureCompletes);
    }
}
