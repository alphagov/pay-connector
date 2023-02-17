package uk.gov.pay.connector.rules;

import io.dropwizard.testing.ConfigOverride;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;

public class DropwizardAppWithPostgresRule extends AppWithPostgresRule {

    public DropwizardAppWithPostgresRule(ConfigOverride... configOverrides) {
        super(configOverrides);
    }

    public DropwizardAppWithPostgresRule(boolean stubPaymentGateways, ConfigOverride... configOverrides) {
        super(stubPaymentGateways, configOverrides);
    }

    @Override
    protected AppRule<ConnectorConfiguration> newApplication(final String configPath,
                                                             final ConfigOverride... configOverrides) {
        return new DropwizardAppRule<>(ConnectorApp.class, configPath, configOverrides);
    }
}
