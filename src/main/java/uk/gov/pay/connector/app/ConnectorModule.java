package uk.gov.pay.connector.app;

import com.google.inject.AbstractModule;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.util.ChargeEventJpaListener;

import javax.inject.Singleton;

public class ConnectorModule extends AbstractModule {
    final ConnectorConfiguration configuration;
    final Environment environment;

    public ConnectorModule(final ConnectorConfiguration configuration, final Environment environment) {
        this.configuration = configuration;
        this.environment = environment;
    }

    @Override
    protected void configure() {
        bind(ConnectorConfiguration.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);
        bind(ChargeEventJpaListener.class).in(Singleton.class);
    }
}
