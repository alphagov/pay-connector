package uk.gov.pay.connector.app;

import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.GraphiteUDP;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import uk.gov.pay.connector.auth.BasicAuthUser;
import uk.gov.pay.connector.auth.SmartpayAccountSpecificAuthenticator;
import uk.gov.pay.connector.filters.LoggingFilter;
import uk.gov.pay.connector.filters.SchemeRewriteFilter;
import uk.gov.pay.connector.healthcheck.CardExecutorServiceHealthCheck;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.resources.*;
import uk.gov.pay.connector.service.Auth3dsDetailsFactory;
import uk.gov.pay.connector.service.CaptureProcessScheduler;
import uk.gov.pay.connector.service.CardCaptureProcess;
import uk.gov.pay.connector.util.DependentResourceWaitCommand;
import uk.gov.pay.connector.util.TrustingSSLSocketFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import java.util.concurrent.TimeUnit;

import static java.util.EnumSet.of;
import static javax.servlet.DispatcherType.REQUEST;

public class ConnectorApp extends Application<ConnectorConfiguration> {

    public static final boolean NON_STRICT_VARIABLE_SUBSTITUTOR = false;

    private static final String SERVICE_METRICS_NODE = "connector";
    private static final int GRAPHITE_SENDING_PERIOD_SECONDS = 10;

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

        initialiseMetrics(configuration, environment);

        environment.jersey().register(injector.getInstance(GatewayAccountResource.class));
        environment.jersey().register(injector.getInstance(ChargeEventsResource.class));
        environment.jersey().register(injector.getInstance(SecurityTokensResource.class));
        environment.jersey().register(injector.getInstance(ChargesApiResource.class));
        environment.jersey().register(injector.getInstance(ChargesFrontendResource.class));
        environment.jersey().register(injector.getInstance(ChargeRefundsResource.class));
        environment.jersey().register(injector.getInstance(NotificationResource.class));
        environment.jersey().register(injector.getInstance(CardResource.class));
        environment.jersey().register(injector.getInstance(CardTypesResource.class));
        environment.jersey().register(injector.getInstance(HealthCheckResource.class));
        environment.jersey().register(injector.getInstance(EmailNotificationResource.class));
        environment.jersey().register(injector.getInstance(SchemeRewriteFilter.class));
        environment.jersey().register(injector.getInstance(Auth3dsDetailsFactory.class));

        setupSchedulers(configuration, environment, injector);

        setupSmartpayBasicAuth(environment, injector.getInstance(SmartpayAccountSpecificAuthenticator.class));

        environment.servlets().addFilter("LoggingFilter", injector.getInstance(LoggingFilter.class))
                .addMappingForUrlPatterns(of(REQUEST), true, ApiPaths.API_VERSION_PATH + "/*");

        environment.healthChecks().register("ping", new Ping());
        environment.healthChecks().register("database", injector.getInstance(DatabaseHealthCheck.class));
        environment.healthChecks().register("cardExecutorService", injector.getInstance(CardExecutorServiceHealthCheck.class));

        setGlobalProxies(configuration);
    }

    private void setupSmartpayBasicAuth(Environment environment, Authenticator<BasicCredentials, BasicAuthUser> authenticator) {
        BasicCredentialAuthFilter<BasicAuthUser> basicCredentialAuthFilter =
                new BasicCredentialAuthFilter.Builder<BasicAuthUser>()
                        .setAuthenticator(authenticator)
                        .buildAuthFilter();

        environment.jersey().register(RolesAllowedDynamicFeature.class);
        environment.jersey().register(new AuthDynamicFeature(basicCredentialAuthFilter));
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(BasicAuthUser.class));
    }

    private void initialiseMetrics(ConnectorConfiguration configuration, Environment environment) {
        GraphiteSender graphiteUDP = new GraphiteUDP(configuration.getGraphiteHost(), Integer.valueOf(configuration.getGraphitePort()));
        GraphiteReporter.forRegistry(environment.metrics())
                .prefixedWith(SERVICE_METRICS_NODE)
                .build(graphiteUDP)
                .start(GRAPHITE_SENDING_PERIOD_SECONDS, TimeUnit.SECONDS);

    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }

    private void setGlobalProxies(ConnectorConfiguration configuration) {
        SSLSocketFactory socketFactory = new TrustingSSLSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);

        System.setProperty("https.proxyHost", configuration.getClientConfiguration().getProxyConfiguration().getHost());
        System.setProperty("https.proxyPort", configuration.getClientConfiguration().getProxyConfiguration().getPort().toString());
    }

    private void setupSchedulers(ConnectorConfiguration configuration, Environment environment, Injector injector) {
        CaptureProcessScheduler captureProcessScheduler = new CaptureProcessScheduler(configuration, environment, injector.getInstance(CardCaptureProcess.class));
        environment.lifecycle().manage(captureProcessScheduler);
    }
}
