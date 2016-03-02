package uk.gov.pay.connector.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.dao.*;

import java.util.Properties;

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

        bind(ITokenDao.class).to(TokenJpaDao.class);
        bind(IChargeDao.class).to(ChargeJpaDao.class);
        bind(IEventDao.class).to(EventJpaDao.class);
        bind(IGatewayAccountDao.class).to(GatewayAccountJpaDao.class);

        install(jpaModule(configuration.getDataSourceFactory()));
    }

    private JpaPersistModule jpaModule(DataSourceFactory dbConfig) {
        final Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.driver", dbConfig.getDriverClass());
        properties.put("javax.persistence.jdbc.url", dbConfig.getUrl());
        properties.put("javax.persistence.jdbc.user", dbConfig.getUser());
        properties.put("javax.persistence.jdbc.password", dbConfig.getPassword());
        final JpaPersistModule jpaModule = new JpaPersistModule("ConnectorUnit");
        jpaModule.properties(properties);

        return jpaModule;
    }

    @Provides
    public ObjectMapper provideObjectMapper() {
        return environment.getObjectMapper();
    }
}
