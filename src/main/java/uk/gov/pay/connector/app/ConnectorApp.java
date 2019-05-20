package uk.gov.pay.connector.app;

import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.GraphiteUDP;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import uk.gov.pay.commons.utils.logging.LoggingFilter;
import uk.gov.pay.commons.utils.xray.Xray;
import uk.gov.pay.connector.cardtype.resource.CardTypesResource;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountExceptionMapper;
import uk.gov.pay.connector.charge.resource.ChargesApiResource;
import uk.gov.pay.connector.charge.resource.ChargesFrontendResource;
import uk.gov.pay.connector.chargeevent.resource.ChargeEventsResource;
import uk.gov.pay.connector.command.RenderStateTransitionGraphCommand;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.UnsupportedOperationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.filters.SchemeRewriteFilter;
import uk.gov.pay.connector.gateway.smartpay.auth.BasicAuthUser;
import uk.gov.pay.connector.gateway.smartpay.auth.SmartpayAccountSpecificAuthenticator;
import uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountResource;
import uk.gov.pay.connector.gatewayaccount.resource.StripeAccountResource;
import uk.gov.pay.connector.gatewayaccount.resource.StripeAccountSetupResource;
import uk.gov.pay.connector.healthcheck.CardExecutorServiceHealthCheck;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.healthcheck.resource.HealthCheckResource;
import uk.gov.pay.connector.paymentprocessor.resource.CardResource;
import uk.gov.pay.connector.paymentprocessor.resource.DiscrepancyResource;
import uk.gov.pay.connector.paymentprocessor.service.CaptureProcessScheduler;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;
import uk.gov.pay.connector.queue.managed.SQSMessageReceiver;
import uk.gov.pay.connector.refund.resource.ChargeRefundsResource;
import uk.gov.pay.connector.refund.resource.SearchRefundsResource;
import uk.gov.pay.connector.report.resource.PerformanceReportResource;
import uk.gov.pay.connector.report.resource.TransactionsSummaryResource;
import uk.gov.pay.connector.token.resource.SecurityTokensResource;
import uk.gov.pay.connector.usernotification.resource.EmailNotificationResource;
import uk.gov.pay.connector.util.DependentResourceWaitCommand;
import uk.gov.pay.connector.util.JsonMappingExceptionMapper;
import uk.gov.pay.connector.util.XrayUtils;
import uk.gov.pay.connector.webhook.resource.NotificationResource;

import java.util.Optional;
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
        bootstrap.addCommand(new RenderStateTransitionGraphCommand());
    }

    @Override
    public void run(ConnectorConfiguration configuration, Environment environment) {
        final Injector injector = createInjector(environment, getModule(configuration, environment));

        injector.getInstance(PersistenceServiceInitialiser.class);

        initialiseMetrics(configuration, environment);

        environment.jersey().register(new ConstraintViolationExceptionMapper());
        environment.jersey().register(new ValidationExceptionMapper());
        environment.jersey().register(new UnsupportedOperationExceptionMapper());
        environment.jersey().register(new LoggingExceptionMapper<>() {});
        environment.jersey().register(new JsonProcessingExceptionMapper());
        environment.jersey().register(new EarlyEofExceptionMapper());
        environment.jersey().register(new JsonMappingExceptionMapper());
        environment.jersey().register(new JsonMappingExceptionMapper());
        environment.jersey().register(new ZeroAmountNotAllowedForGatewayAccountExceptionMapper());

        environment.jersey().register(injector.getInstance(GatewayAccountResource.class));
        environment.jersey().register(injector.getInstance(StripeAccountSetupResource.class));
        environment.jersey().register(injector.getInstance(StripeAccountResource.class));
        environment.jersey().register(injector.getInstance(ChargeEventsResource.class));
        environment.jersey().register(injector.getInstance(SecurityTokensResource.class));
        environment.jersey().register(injector.getInstance(ChargesApiResource.class));
        environment.jersey().register(injector.getInstance(ChargesFrontendResource.class));
        environment.jersey().register(injector.getInstance(ChargeRefundsResource.class));
        environment.jersey().register(injector.getInstance(TransactionsSummaryResource.class));
        environment.jersey().register(injector.getInstance(NotificationResource.class));
        environment.jersey().register(injector.getInstance(CardResource.class));
        environment.jersey().register(injector.getInstance(CardTypesResource.class));
        environment.jersey().register(injector.getInstance(HealthCheckResource.class));
        environment.jersey().register(injector.getInstance(EmailNotificationResource.class));
        environment.jersey().register(injector.getInstance(SchemeRewriteFilter.class));
        environment.jersey().register(injector.getInstance(PerformanceReportResource.class));
        environment.jersey().register(injector.getInstance(SearchRefundsResource.class));
        environment.jersey().register(injector.getInstance(DiscrepancyResource.class));

        setupSchedulers(configuration, environment, injector);

        setupSmartpayBasicAuth(environment, injector.getInstance(SmartpayAccountSpecificAuthenticator.class));

        environment.servlets().addFilter("LoggingFilter", injector.getInstance(LoggingFilter.class))
                .addMappingForUrlPatterns(of(REQUEST), true, "/v1/*");

        environment.healthChecks().register("ping", new Ping());
        environment.healthChecks().register("database", injector.getInstance(DatabaseHealthCheck.class));
        environment.healthChecks().register("cardExecutorService", injector.getInstance(CardExecutorServiceHealthCheck.class));
        
        if (configuration.isXrayEnabled())
            Xray.init(environment, "pay-connector", Optional.empty(), "/v1/*");
    }

    protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
        return new ConnectorModule(configuration, environment);
    }

    private Injector createInjector(Environment environment, Module module) {
        final Injector injector = Guice.createInjector(module);
        environment.lifecycle().manage(InjectorLookup.registerInjector(this, injector));
        return injector;
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

    private void setupSchedulers(ConnectorConfiguration configuration, Environment environment, Injector injector) {
        CaptureProcessScheduler captureProcessScheduler = new CaptureProcessScheduler(configuration,
                environment, injector.getInstance(CardCaptureProcess.class), injector.getInstance(XrayUtils.class));
        environment.lifecycle().manage(captureProcessScheduler);

        environment.lifecycle().manage(injector.getInstance(SQSMessageReceiver.class));
    }
}
