package uk.gov.pay.connector.app;

import io.dropwizard.Application;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.auth.basic.BasicAuthFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.bundles.DBIExceptionsBundle;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.auth.SmartpayAuthenticator;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.resources.CardResource;
import uk.gov.pay.connector.resources.ChargesApiResource;
import uk.gov.pay.connector.resources.ChargesFrontendResource;
import uk.gov.pay.connector.resources.GatewayAccountResource;
import uk.gov.pay.connector.resources.NotificationResource;
import uk.gov.pay.connector.resources.SecurityTokensResource;
import uk.gov.pay.connector.service.CardService;
import uk.gov.pay.connector.service.ClientFactory;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.util.DbWaitCommand;

public class ConnectorApp extends Application<ConnectorConfiguration> {
    private DBI jdbi;

    @Override
    public void initialize(Bootstrap<ConnectorConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor()
                )
        );

        bootstrap.addBundle(new DBIExceptionsBundle());

        bootstrap.addBundle(new MigrationsBundle<ConnectorConfiguration>() {
            @Override
            public DataSourceFactory getDataSourceFactory(ConnectorConfiguration configuration) {
                return configuration.getDataSourceFactory();
            }
        });

        bootstrap.addCommand(new DbWaitCommand());
    }

    @Override
    public void run(ConnectorConfiguration conf, Environment environment) throws Exception {
        DataSourceFactory dataSourceFactory = conf.getDataSourceFactory();

        environment.healthChecks().register("ping", new Ping());

        jdbi = new DBIFactory().build(environment, dataSourceFactory, "postgresql");

        ChargeDao chargeDao = new ChargeDao(jdbi);
        TokenDao tokenDao = new TokenDao(jdbi);
        GatewayAccountDao gatewayAccountDao = new GatewayAccountDao(jdbi);

        ClientFactory clientFactory = new ClientFactory(environment, conf);

        PaymentProviders providers = new PaymentProviders(conf, clientFactory, environment.getObjectMapper());
        CardService cardService = new CardService(gatewayAccountDao, chargeDao, providers);

        environment.jersey().register(new SecurityTokensResource(tokenDao));
        environment.jersey().register(new NotificationResource(providers, chargeDao));
        environment.jersey().register(new ChargesApiResource(chargeDao, tokenDao, gatewayAccountDao, conf.getLinks()));
        environment.jersey().register(new ChargesFrontendResource(chargeDao, gatewayAccountDao));
        environment.jersey().register(new CardResource(cardService));
        environment.jersey().register(new GatewayAccountResource(gatewayAccountDao, conf));

        environment.jersey().register(
                AuthFactory.binder(
                        new BasicAuthFactory<>(
                                new SmartpayAuthenticator(
                                        conf.getSmartpayConfig().getNotification()),
                                "",
                                String.class)));

        environment.healthChecks().register("database", new DatabaseHealthCheck(jdbi, dataSourceFactory.getValidationQuery()));
    }

    public DBI getJdbi() {
        return jdbi;
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }
}
