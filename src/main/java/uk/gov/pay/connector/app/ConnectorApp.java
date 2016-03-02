package uk.gov.pay.connector.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import uk.gov.pay.connector.auth.BasicAuthUser;
import uk.gov.pay.connector.auth.SmartpayAuthenticator;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.resources.*;

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
    public void run(ConnectorConfiguration config, Environment environment) throws Exception {

        final Injector injector = Guice.createInjector(
                new ConnectorModule(config, environment),
                createJpaModule(config.getDataSourceFactory()));

        environment.servlets().addFilter("persistFilter", injector.getInstance(PersistFilter.class))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        environment.jersey().register(injector.getInstance(GatewayAccountJpaResource.class));
        environment.jersey().register(injector.getInstance(EventsApiJpaResource.class));
        environment.jersey().register(injector.getInstance(SecurityTokensResource.class));
        environment.jersey().register(injector.getInstance(ChargesApiResource.class));
        environment.jersey().register(injector.getInstance(ChargesFrontendResource.class));
        environment.jersey().register(injector.getInstance(NotificationResource.class));
        environment.jersey().register(injector.getInstance(CardResource.class));
        environment.jersey().register(injector.getInstance(GatewayAccountResource.class));

        DataSourceFactory dataSourceFactory = config.getDataSourceFactory();

        environment.healthChecks().register("ping", new Ping());

        environment.jersey().register(RolesAllowedDynamicFeature.class);

        SmartpayAuthenticator smartPayAuth = new SmartpayAuthenticator(config.getSmartpayConfig().getNotification());

        BasicCredentialAuthFilter<BasicAuthUser> basicCredentialAuthFilter =
            new BasicCredentialAuthFilter.Builder<BasicAuthUser>()
                .setAuthenticator(smartPayAuth)
                .buildAuthFilter();

        environment.jersey().register(new AuthDynamicFeature(basicCredentialAuthFilter));
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(BasicAuthUser.class));

        environment.healthChecks().register("database", new DatabaseHealthCheck(injector.getProvider(EntityManager.class), dataSourceFactory.getValidationQuery()));
    }

    private JpaPersistModule createJpaModule(final DataSourceFactory dbConfig) {
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
