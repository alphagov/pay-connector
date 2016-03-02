package uk.gov.pay.connector.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.dao.*;
import uk.gov.pay.connector.resources.EventsApiJpaResource;
import uk.gov.pay.connector.resources.GatewayAccountJpaResource;
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
        bind(ITokenDao.class).to(TokenJpaDao.class);
        bind(IChargeDao.class).to(ChargeJpaDao.class);
        bind(IEventDao.class).to(EventJpaDao.class);
        bind(IGatewayAccountDao.class).to(GatewayAccountJpaDao.class);
    }

    @Provides
    public ObjectMapper provideObjectMapper() {
        return environment.getObjectMapper();
    }
}
