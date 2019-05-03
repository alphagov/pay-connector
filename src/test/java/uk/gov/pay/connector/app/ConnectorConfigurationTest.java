package uk.gov.pay.connector.app;

import io.dropwizard.util.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-config.yaml")
public class ConnectorConfigurationTest {

    @DropwizardTestContext
    protected TestContext testContext;

    @Test
    public void shouldParseConfiguration() {
        JerseyClientOverrides jerseyClientOverrides = testContext.getConnectorConfiguration().getWorldpayConfig().getJerseyClientOverrides().get();

        Duration authReadTimeout = jerseyClientOverrides.getAuth().getReadTimeout();
        assertThat(authReadTimeout, is(Duration.milliseconds(222)));

        CaptureProcessConfig captureProcessConfig = testContext.getConnectorConfiguration().getCaptureProcessConfig();
        assertThat(captureProcessConfig.getRetryFailuresEvery(), is(Duration.minutes(60)));
        assertThat(captureProcessConfig.getRetryFailuresEveryAsJavaDuration(), is(java.time.Duration.ofMinutes(60)));
        assertThat(captureProcessConfig.getMaximumRetries(), is(48));
        assertThat(captureProcessConfig.getBatchSize(), is(10));
    }

}
