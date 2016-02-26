package uk.gov.pay.connector.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.auth.basic.BasicAuthFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.auth.SmartpayAuthenticator;
import uk.gov.pay.connector.dao.*;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.resources.*;
import uk.gov.pay.connector.service.CardService;
import uk.gov.pay.connector.service.ClientFactory;
import uk.gov.pay.connector.service.PaymentProviders;

import javax.persistence.EntityManager;
import javax.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.Properties;

public class ConnectorApp extends Application<ConnectorConfiguration> {

    @Override
    public void initialize(Bootstrap<ConnectorConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor()
                )
        );

        bootstrap.addBundle(new MigrationsBundle<ConnectorConfiguration>() {
            @Override
            public DataSourceFactory getDataSourceFactory(ConnectorConfiguration configuration) {
                return configuration.getDataSourceFactory();
            }
        });
    }

    @Override
    public void run(ConnectorConfiguration conf, Environment environment) throws Exception {

        final Injector injector = Guice.createInjector(new ConnectorModule(conf, environment), createJpaModule(conf.getDatabaseConfig()));
        environment.servlets().addFilter("persistFilter", injector.getInstance(PersistFilter.class))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
        environment.jersey().register(injector.getInstance(GatewayAccountJpaResource.class));
        environment.jersey().register(injector.getInstance(EventsApiJpaResource.class));

        DataSourceFactory dataSourceFactory = conf.getDataSourceFactory();

        environment.healthChecks().register("ping", new Ping());

        IEventDao eventDao = injector.getInstance(EventJpaDao.class);
        IChargeDao chargeDao = injector.getInstance(ChargeJpaDao.class);
        ITokenDao tokenDao = injector.getInstance(TokenJpaDao.class);
        IGatewayAccountDao gatewayAccountDao = injector.getInstance(GatewayAccountJpaDao.class);

        ClientFactory clientFactory = new ClientFactory(environment, conf);

        PaymentProviders providers = new PaymentProviders(conf, clientFactory, environment.getObjectMapper());
        CardService cardService = new CardService(gatewayAccountDao, chargeDao, providers);

        environment.jersey().register(new SecurityTokensResource(tokenDao));
        environment.jersey().register(new NotificationResource(providers, chargeDao, gatewayAccountDao));
        environment.jersey().register(new ChargesApiResource(chargeDao, tokenDao, gatewayAccountDao, eventDao, conf.getLinks()));
        environment.jersey().register(new ChargesFrontendResource(chargeDao));
        environment.jersey().register(new CardResource(cardService));
        environment.jersey().register(new GatewayAccountResource(gatewayAccountDao, conf));

        environment.jersey().register(
                AuthFactory.binder(
                        new BasicAuthFactory<>(
                                new SmartpayAuthenticator(
                                        conf.getSmartpayConfig().getNotification()),
                                "",
                                String.class)));

        environment.healthChecks().register("database", new DatabaseHealthCheck(injector.getProvider(EntityManager.class), dataSourceFactory.getValidationQuery()));
    }

    private JpaPersistModule createJpaModule(final DatabaseConfig dbConfig) {
        final Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.driver", dbConfig.getDriverClass());
        properties.put("javax.persistence.jdbc.url", dbConfig.getUrl());
        properties.put("javax.persistence.jdbc.user", dbConfig.getUser());
        properties.put("javax.persistence.jdbc.password", dbConfig.getPassword());

        final JpaPersistModule jpaModule = new JpaPersistModule("ConnectorUnit");
        jpaModule.properties(properties);

        return jpaModule;
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }
}
