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
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.paymentprocessor.service.CaptureProcessScheduler;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresRule;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.PortFactory;
import uk.gov.pay.connector.util.XrayUtils;

import java.util.List;
import java.util.Map;

import static io.dropwizard.testing.ConfigOverride.config;
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

public class CaptureProcessSchedulerITest {

    private static final String PAYMENT_PROVIDER = "worldpay";

    private int CAPTURE_MAX_RETRIES = 1;
    private static final String TRANSACTION_ID = "7914440428682669";
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of(
                    CREDENTIALS_MERCHANT_ID, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password",
                    CREDENTIALS_SHA_IN_PASSPHRASE, "sha-passphraser"
            );

    protected int port = PortFactory.findFreePort();

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("captureProcessConfig.maximumRetries", Integer.toString(CAPTURE_MAX_RETRIES)),
            config("captureProcessConfig.schedulerThreads", "2"),
            config("captureProcessConfig.schedulerInitialDelayInSeconds", "0"),
            config("captureProcessConfig.schedulerRandomIntervalMinimumInSeconds", "1"),
            config("captureProcessConfig.schedulerRandomIntervalMaximumInSeconds", "1")
    );

    @Before
    public void setup() {
        Logger root = (Logger) LoggerFactory.getLogger(CardCaptureProcess.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void schedulerShouldStartMultipleThreadsAsPerConfig_AndCaptureCharge() throws InterruptedException {

        DatabaseFixtures.TestCharge testCharge = ChargeUtils.createTestCharge(app.getDatabaseTestHelper(), PAYMENT_PROVIDER, CAPTURE_APPROVED,
                CREDENTIALS, TRANSACTION_ID);
        new WorldpayMockClient().mockCaptureSuccess();

        CaptureProcessScheduler captureProcessScheduler = new CaptureProcessScheduler(app.getConf(),
                app.getEnvironment(), app.getInstanceFromGuiceContainer(CardCaptureProcess.class),
                app.getInstanceFromGuiceContainer(XrayUtils.class));

        captureProcessScheduler.start();

        Thread.sleep(1000L); // Mininum configurable interval for capture scheduler

        // Expected below : For 2 scheduler threads with a charge in CAPTURE_APPROVED state
        // Thread 1: Capturing: 1 of 1 charges ,  Capturing [1 of 1] [chargeId=...] ,  Capture complete ...
        // Thread 2: Capture complete ....
        verify(mockAppender, times(4)).doAppend(loggingEventArgumentCaptor.capture());
        assertThatTwoThreadsAreCompletingCaptureProcess(loggingEventArgumentCaptor);

        assertThat(app.getDatabaseTestHelper().getChargeStatus(testCharge.getChargeId()), Matchers.is(CAPTURE_SUBMITTED.getValue()));
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
