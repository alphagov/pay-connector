package uk.gov.pay.connector.app;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ConnectorConfigurationTest {

    @Rule
    public final DropwizardAppRule<ConnectorConfiguration> RULE =
            new DropwizardAppRule<ConnectorConfiguration>(ConnectorApp.class,
                    ResourceHelpers.resourceFilePath("config/test-config.yaml"));


    @Test
    public void shouldParseConfiguration() {
        Map<String, Map<String, String>> jerseyClientOverrides = RULE.getConfiguration().getWorldpayConfig().getJerseyClientOverrides();

        assertThat(jerseyClientOverrides.get("auth").get("timeout"), is("111ms"));
    }

}
