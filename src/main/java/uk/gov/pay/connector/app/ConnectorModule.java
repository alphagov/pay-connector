package uk.gov.pay.connector.app;

import com.google.inject.AbstractModule;
import io.dropwizard.setup.Environment;

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
//        bind(Dao.class).to(DaoImpl.class).in(Singleton.class);
//        bind(PlayerService.class).in(Singleton.class);
//        bind(ScoreService.class).in(Singleton.class);
    }
}
