package uk.gov.pay.connector.app;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.dropwizard.util.Duration;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ConnectorConfigurationTest {

    @Rule
    public final DropwizardAppRule<ConnectorConfiguration> RULE =
            new DropwizardAppRule<ConnectorConfiguration>(ConnectorApp.class,
                    ResourceHelpers.resourceFilePath("config/test-config.yaml"));


    @Test
    public void shouldParseConfiguration() {
        JerseyClientOverrides jerseyClientOverrides = RULE.getConfiguration().getWorldpayConfig().getJerseyClientOverrides().get();

        Duration authReadTimeout = jerseyClientOverrides.getAuth().getReadTimeout();

        assertThat(authReadTimeout, is(Duration.milliseconds(222)));
    }

}
