package uk.gov.pay.connector.util;

import io.dropwizard.core.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;

import static org.mockito.Mockito.mock;

public class ConnectorModuleWithOverrides extends ConnectorModule {

    public static final ReverseDnsLookup reverseDnsLookup = mock(ReverseDnsLookup.class);

    public ConnectorModuleWithOverrides(ConnectorConfiguration configuration, Environment environment) {
        super(configuration, environment);
    }

    @Override
    protected ReverseDnsLookup getReverseDnsLookup() {
        return reverseDnsLookup;
    }
}
