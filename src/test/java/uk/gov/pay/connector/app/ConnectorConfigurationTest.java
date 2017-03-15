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
        JerseyClientOverrides jerseyClientOverrides = RULE.getConfiguration().getWorldpayConfig().getJerseyClientOverrides();

        Duration authTimeout = jerseyClientOverrides.getAuth().getTimeout();
        Duration authReadTimeout = jerseyClientOverrides.getAuth().getReadTimeout();
        Duration authConnectionTimeout = jerseyClientOverrides.getAuth().getConnectionTimeout();

        assertThat(authTimeout, is(Duration.milliseconds(111)));
        assertThat(authReadTimeout, is(Duration.milliseconds(222)));
        assertThat(authConnectionTimeout, is(Duration.milliseconds(333)));
    }

}
