package uk.gov.pay.connector.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
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
import uk.gov.pay.connector.util.DependentResourceWaitCommand;

public class ConnectorApp extends Application<ConnectorConfiguration> {

    public static final boolean NON_STRICT_VARIABLE_SUBSTITUTOR = false;

    @Override
    public void initialize(Bootstrap<ConnectorConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(NON_STRICT_VARIABLE_SUBSTITUTOR)
                )
        );

        bootstrap.addBundle(new MigrationsBundle<ConnectorConfiguration>() {
            @Override
            public DataSourceFactory getDataSourceFactory(ConnectorConfiguration configuration) {
                return configuration.getDataSourceFactory();
            }
        });

        bootstrap.addCommand(new DependentResourceWaitCommand());
    }

    @Override
    public void run(ConnectorConfiguration configuration, Environment environment) throws Exception {

        final Injector injector = Guice.createInjector(new ConnectorModule(configuration, environment));

        injector.getInstance(PersistenceServiceInitialiser.class);

        environment.jersey().register(injector.getInstance(GatewayAccountResource.class));
        environment.jersey().register(injector.getInstance(ChargeEventsResource.class));
        environment.jersey().register(injector.getInstance(SecurityTokensResource.class));
        environment.jersey().register(injector.getInstance(ChargesResource.class));
        environment.jersey().register(injector.getInstance(ChargesFrontendResource.class));
        environment.jersey().register(injector.getInstance(NotificationResource.class));
        environment.jersey().register(injector.getInstance(CardResource.class));
        environment.jersey().register(injector.getInstance(GatewayAccountResource.class));
        setupSmartpayBasicAuth(environment, configuration.getSmartpayConfig());

        environment.healthChecks().register("ping", new Ping());
        environment.healthChecks().register("database", injector.getInstance(DatabaseHealthCheck.class));
    }

    private void setupSmartpayBasicAuth(Environment environment, SmartpayCredentialsConfig smartpayConfig) {
        SmartpayAuthenticator smartpayAuthenticator = new SmartpayAuthenticator(smartpayConfig.getNotification());
        BasicCredentialAuthFilter<BasicAuthUser> basicCredentialAuthFilter =
                new BasicCredentialAuthFilter.Builder<BasicAuthUser>()
                        .setAuthenticator(smartpayAuthenticator)
                        .buildAuthFilter();

        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthDynamicFeature(basicCredentialAuthFilter));
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(BasicAuthUser.class));
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }
}
