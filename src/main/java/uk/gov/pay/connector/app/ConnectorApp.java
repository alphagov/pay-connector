package uk.gov.pay.connector.app;

import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.GraphiteUDP;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.core.setup.Environment;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.MetricsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.exception.AgreementNotFoundExceptionMapper;
import uk.gov.pay.connector.agreement.exception.RecurringCardPaymentsNotAllowedExceptionMapper;
import uk.gov.pay.connector.agreement.resource.AgreementsApiResource;
import uk.gov.pay.connector.cardtype.resource.CardTypesResource;
import uk.gov.pay.connector.charge.exception.AgreementIdWithIncompatibleOtherOptionsExceptionMapper;
import uk.gov.pay.connector.charge.exception.AgreementMissingPaymentInstrumentExceptionMapper;
import uk.gov.pay.connector.charge.exception.AgreementNotFoundBadRequestExceptionMapper;
import uk.gov.pay.connector.charge.exception.AuthorisationModeAgreementRequiresAgreementIdExceptionMapper;
import uk.gov.pay.connector.charge.exception.CardNumberInPaymentLinkReferenceExceptionMapper;
import uk.gov.pay.connector.charge.exception.ConflictWebApplicationExceptionMapper;
import uk.gov.pay.connector.charge.exception.ErrorListMapper;
import uk.gov.pay.connector.charge.exception.GatewayAccountDisabledExceptionMapper;
import uk.gov.pay.connector.charge.exception.IdempotencyKeyUsedExceptionMapper;
import uk.gov.pay.connector.charge.exception.IncorrectAuthorisationModeForSavePaymentToAgreementExceptionMapper;
import uk.gov.pay.connector.charge.exception.InvalidAttributeValueExceptionMapper;
import uk.gov.pay.connector.charge.exception.MissingMandatoryAttributeExceptionMapper;
import uk.gov.pay.connector.charge.exception.MotoPaymentNotAllowedForGatewayAccountExceptionMapper;
import uk.gov.pay.connector.charge.exception.PaymentInstrumentNotActiveExceptionMapper;
import uk.gov.pay.connector.charge.exception.SavePaymentInstrumentToAgreementRequiresAgreementIdExceptionMapper;
import uk.gov.pay.connector.charge.exception.TelephonePaymentNotificationsNotAllowedExceptionMapper;
import uk.gov.pay.connector.charge.exception.UnexpectedAttributeExceptionMapper;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationApiNotAllowedForGatewayAccountExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationErrorExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationRejectedExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationTimedOutExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.CardNumberRejectedExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenAlreadyUsedExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenInvalidExceptionMapper;
import uk.gov.pay.connector.charge.exception.motoapi.OneTimeTokenUsageInvalidForMotoApiExceptionMapper;
import uk.gov.pay.connector.charge.resource.ChargesApiResource;
import uk.gov.pay.connector.charge.resource.ChargesFrontendResource;
import uk.gov.pay.connector.charge.resource.GatewayCleanupResource;
import uk.gov.pay.connector.chargeevent.resource.ChargeEventsResource;
import uk.gov.pay.connector.command.RenderStateTransitionGraphCommand;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.UnsupportedOperationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.events.resource.EmittedEventResource;
import uk.gov.pay.connector.expunge.resource.ExpungeResource;
import uk.gov.pay.connector.filters.LoggingMDCRequestFilter;
import uk.gov.pay.connector.filters.LoggingMDCResponseFilter;
import uk.gov.pay.connector.filters.SchemeRewriteFilter;
import uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountResource;
import uk.gov.pay.connector.gatewayaccount.resource.StripeAccountResource;
import uk.gov.pay.connector.gatewayaccount.resource.StripeAccountSetupResource;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.CredentialsNotFoundBadRequestExceptionMapper;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.MissingCredentialsForRecurringPaymentExceptionMapper;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.NoCredentialsExistForProviderExceptionMapper;
import uk.gov.pay.connector.gatewayaccountcredentials.exception.NoCredentialsInUsableStateExceptionMapper;
import uk.gov.pay.connector.gatewayaccountcredentials.resource.GatewayAccountCredentialsResource;
import uk.gov.pay.connector.healthcheck.CardExecutorServiceHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.healthcheck.SQSHealthCheck;
import uk.gov.pay.connector.healthcheck.resource.HealthCheckResource;
import uk.gov.pay.connector.paymentprocessor.resource.CardResource;
import uk.gov.pay.connector.paymentprocessor.resource.DiscrepancyResource;
import uk.gov.pay.connector.queue.managed.CaptureMessageReceiver;
import uk.gov.pay.connector.queue.managed.PayoutReconcileMessageReceiver;
import uk.gov.pay.connector.queue.managed.StateTransitionMessageReceiver;
import uk.gov.pay.connector.queue.managed.TaskQueueMessageReceiver;
import uk.gov.pay.connector.refund.resource.RefundReversalResource;
import uk.gov.pay.connector.refund.resource.RefundsResource;
import uk.gov.pay.connector.report.resource.ParityCheckerResource;
import uk.gov.pay.connector.token.exception.TokenNotFoundExceptionMapper;
import uk.gov.pay.connector.token.resource.SecurityTokensResource;
import uk.gov.pay.connector.usernotification.resource.EmailNotificationResource;
import uk.gov.pay.connector.util.DependentResourceWaitCommand;
import uk.gov.pay.connector.util.JsonMappingExceptionMapper;
import uk.gov.pay.connector.webhook.resource.NotificationResource;
import uk.gov.service.payments.commons.utils.healthchecks.DatabaseHealthCheck;
import uk.gov.service.payments.commons.utils.metrics.DatabaseMetricsService;
import uk.gov.service.payments.logging.GovUkPayDropwizardRequestJsonLogLayoutFactory;
import uk.gov.service.payments.logging.LoggingFilter;
import uk.gov.service.payments.logging.LogstashConsoleAppenderFactory;
import uk.gov.service.payments.logging.SentryAppenderFactory;

import java.util.concurrent.TimeUnit;

import static java.util.EnumSet.of;
import static javax.servlet.DispatcherType.REQUEST;

public class ConnectorApp extends Application<ConnectorConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorApp.class);

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
        bootstrap.getObjectMapper().getSubtypeResolver().registerSubtypes(LogstashConsoleAppenderFactory.class);
        bootstrap.getObjectMapper().getSubtypeResolver().registerSubtypes(SentryAppenderFactory.class);
        bootstrap.getObjectMapper().getSubtypeResolver().registerSubtypes(GovUkPayDropwizardRequestJsonLogLayoutFactory.class);
    }

    @Override
    public void run(ConnectorConfiguration configuration, Environment environment) {
        final Injector injector = createInjector(environment, getModule(configuration, environment));

        injector.getInstance(PersistenceServiceInitialiser.class);

        initialiseMetrics(configuration, environment);

        environment.jersey().register(new ConstraintViolationExceptionMapper());
        environment.jersey().register(new ValidationExceptionMapper());
        environment.jersey().register(new UnsupportedOperationExceptionMapper());
        environment.jersey().register(new LoggingExceptionMapper<>() {
        });
        environment.jersey().register(new JsonProcessingExceptionMapper());
        environment.jersey().register(new EarlyEofExceptionMapper());
        environment.jersey().register(new JsonMappingExceptionMapper());
        environment.jersey().register(new JsonMappingExceptionMapper());
        environment.jersey().register(new ZeroAmountNotAllowedForGatewayAccountExceptionMapper());
        environment.jersey().register(new ConflictWebApplicationExceptionMapper());
        environment.jersey().register(new MotoPaymentNotAllowedForGatewayAccountExceptionMapper());
        environment.jersey().register(new TelephonePaymentNotificationsNotAllowedExceptionMapper());
        environment.jersey().register(new NoCredentialsExistForProviderExceptionMapper());
        environment.jersey().register(new CredentialsNotFoundBadRequestExceptionMapper());
        environment.jersey().register(new NoCredentialsInUsableStateExceptionMapper());
        environment.jersey().register(new SavePaymentInstrumentToAgreementRequiresAgreementIdExceptionMapper());
        environment.jersey().register(new IncorrectAuthorisationModeForSavePaymentToAgreementExceptionMapper());
        environment.jersey().register(new AuthorisationModeAgreementRequiresAgreementIdExceptionMapper());
        environment.jersey().register(new AgreementIdWithIncompatibleOtherOptionsExceptionMapper());
        environment.jersey().register(new AgreementNotFoundExceptionMapper());
        environment.jersey().register(new AgreementNotFoundBadRequestExceptionMapper());
        environment.jersey().register(new ErrorListMapper());
        environment.jersey().register(new AgreementMissingPaymentInstrumentExceptionMapper());
        environment.jersey().register(new PaymentInstrumentNotActiveExceptionMapper());
        environment.jersey().register(new OneTimeTokenInvalidExceptionMapper());
        environment.jersey().register(new OneTimeTokenAlreadyUsedExceptionMapper());
        environment.jersey().register(new OneTimeTokenUsageInvalidForMotoApiExceptionMapper());
        environment.jersey().register(new InvalidAttributeValueExceptionMapper());
        environment.jersey().register(new MissingMandatoryAttributeExceptionMapper());
        environment.jersey().register(new UnexpectedAttributeExceptionMapper());
        environment.jersey().register(new CardNumberRejectedExceptionMapper());
        environment.jersey().register(new AuthorisationApiNotAllowedForGatewayAccountExceptionMapper());
        environment.jersey().register(new AuthorisationErrorExceptionMapper());
        environment.jersey().register(new AuthorisationRejectedExceptionMapper());
        environment.jersey().register(new AuthorisationTimedOutExceptionMapper());
        environment.jersey().register(new GatewayAccountDisabledExceptionMapper());
        environment.jersey().register(new RecurringCardPaymentsNotAllowedExceptionMapper());
        environment.jersey().register(new MissingCredentialsForRecurringPaymentExceptionMapper());
        environment.jersey().register(new IdempotencyKeyUsedExceptionMapper());
        environment.jersey().register(new TokenNotFoundExceptionMapper());
        environment.jersey().register(new CardNumberInPaymentLinkReferenceExceptionMapper());

        environment.jersey().register(injector.getInstance(GatewayAccountResource.class));
        environment.jersey().register(injector.getInstance(StripeAccountSetupResource.class));
        environment.jersey().register(injector.getInstance(StripeAccountResource.class));
        environment.jersey().register(injector.getInstance(ChargeEventsResource.class));
        environment.jersey().register(injector.getInstance(SecurityTokensResource.class));
        environment.jersey().register(injector.getInstance(ChargesApiResource.class));
        environment.jersey().register(injector.getInstance(ExpungeResource.class));
        environment.jersey().register(injector.getInstance(ChargesFrontendResource.class));
        environment.jersey().register(injector.getInstance(RefundsResource.class));
        environment.jersey().register(injector.getInstance(RefundReversalResource.class));
        environment.jersey().register(injector.getInstance(NotificationResource.class));
        environment.jersey().register(injector.getInstance(CardResource.class));
        environment.jersey().register(injector.getInstance(CardTypesResource.class));
        environment.jersey().register(injector.getInstance(HealthCheckResource.class));
        environment.jersey().register(injector.getInstance(EmailNotificationResource.class));
        environment.jersey().register(injector.getInstance(SchemeRewriteFilter.class));
        environment.jersey().register(injector.getInstance(DiscrepancyResource.class));
        environment.jersey().register(injector.getInstance(EmittedEventResource.class));
        environment.jersey().register(injector.getInstance(GatewayAccountCredentialsResource.class));
        environment.jersey().register(injector.getInstance(GatewayCleanupResource.class));
        environment.jersey().register(injector.getInstance(ParityCheckerResource.class));
        environment.jersey().register(injector.getInstance(LoggingMDCRequestFilter.class));
        environment.jersey().register(injector.getInstance(LoggingMDCResponseFilter.class));
        environment.jersey().register(injector.getInstance(AgreementsApiResource.class));

        if (configuration.getCaptureProcessConfig().getBackgroundProcessingEnabled()) {
            setupSchedulers(environment, injector);
        }
        environment.lifecycle().manage(injector.getInstance(PayoutReconcileMessageReceiver.class));
        environment.lifecycle().manage(injector.getInstance(TaskQueueMessageReceiver.class));

        environment.servlets().addFilter("LoggingFilter", injector.getInstance(LoggingFilter.class))
                .addMappingForUrlPatterns(of(REQUEST), true, "/v1/*");

        environment.healthChecks().register("ping", new Ping());
        environment.healthChecks().register("database", new DatabaseHealthCheck(configuration.getDataSourceFactory()));
        environment.healthChecks().register("cardExecutorService", injector.getInstance(CardExecutorServiceHealthCheck.class));
        environment.healthChecks().register("sqsQueue", injector.getInstance(SQSHealthCheck.class));
    }

    protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
        return new ConnectorModule(configuration, environment);
    }

    private Injector createInjector(Environment environment, Module module) {
        final Injector injector = Guice.createInjector(module);
        environment.lifecycle().manage(InjectorLookup.registerInjector(this, injector));
        return injector;
    }

    private void initialiseMetrics(ConnectorConfiguration configuration, Environment environment) {
        DatabaseMetricsService metricsService = new DatabaseMetricsService(configuration.getDataSourceFactory(), environment.metrics(), "connector");

        environment
                .lifecycle()
                .scheduledExecutorService("metricscollector")
                .threads(1)
                .build()
                .scheduleAtFixedRate(metricsService::updateMetricData, 0, GRAPHITE_SENDING_PERIOD_SECONDS / 2, TimeUnit.SECONDS);

        initialiseGraphiteMetrics(configuration, environment);

        CollectorRegistry collectorRegistry = CollectorRegistry.defaultRegistry;
        collectorRegistry.register(new DropwizardExports(environment.metrics()));
        environment.admin().addServlet("prometheusMetrics", new MetricsServlet(collectorRegistry.defaultRegistry)).addMapping("/metrics");
    }

    /**
     * Graphite metric config to be deleted when we've completely moved to Prometheus
     */
    private void initialiseGraphiteMetrics(ConnectorConfiguration configuration, Environment environment) {
        GraphiteSender graphiteUDP = new GraphiteUDP(configuration.getGraphiteHost(), Integer.parseInt(configuration.getGraphitePort()));
        GraphiteReporter.forRegistry(environment.metrics())
                .prefixedWith(SERVICE_METRICS_NODE)
                .convertRatesTo(TimeUnit.MINUTES)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build(graphiteUDP)
                .start(GRAPHITE_SENDING_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }

    private void setupSchedulers(Environment environment, Injector injector) {
        environment.lifecycle().manage(injector.getInstance(CaptureMessageReceiver.class));
        environment.lifecycle().manage(injector.getInstance(StateTransitionMessageReceiver.class));
    }
}
