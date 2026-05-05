package uk.gov.pay.connector.util;

import io.dropwizard.core.setup.Environment;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;

public class ConnectorAppWithCustomInjector extends ConnectorApp {

    @Override
    protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
        return new ConnectorModuleWithOverrides(configuration, environment);
    }
}

