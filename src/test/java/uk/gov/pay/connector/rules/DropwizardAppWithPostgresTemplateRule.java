package uk.gov.pay.connector.rules;

import io.dropwizard.testing.ConfigOverride;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;

public class DropwizardAppWithPostgresTemplateRule extends AppWithPostgresWithTemplateRule {

    public DropwizardAppWithPostgresTemplateRule(ConfigOverride... configOverrides) {
        super(configOverrides);
    }

    @Override
    protected AppRule<ConnectorConfiguration> newApplication(final String configPath,
                                                             final ConfigOverride... configOverrides) {
        return new DropwizardAppRule<>(ConnectorApp.class, configPath, configOverrides);
    }
}
