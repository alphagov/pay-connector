package uk.gov.pay.connector.app;

import io.dropwizard.Application;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.auth.basic.BasicAuthFactory;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.bundles.DBIExceptionsBundle;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientProperties;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.auth.SmartpayAuthenticator;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.resources.*;
import uk.gov.pay.connector.service.CardService;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.util.DbConnectionChecker;

import javax.ws.rs.client.Client;

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
    }

    @Override
    public void run(ConnectorConfiguration conf, Environment environment) throws Exception {
        DataSourceFactory dataSourceFactory = conf.getDataSourceFactory();

        DbConnectionChecker checker = new DbConnectionChecker(
                dataSourceFactory.getUrl(),
                dataSourceFactory.getUser(),
                dataSourceFactory.getPassword()
        );
        checker.waitForPostgresToStart();

        environment.healthChecks().register("ping", new Ping());

        jdbi = new DBIFactory()
                .build(environment, dataSourceFactory, "postgresql");

        ChargeDao chargeDao = new ChargeDao(jdbi);
        TokenDao tokenDao = new TokenDao(jdbi);
        GatewayAccountDao gatewayAccountDao = new GatewayAccountDao(jdbi);

        Client client = createJerseyClient(environment, conf);

        PaymentProviders providers = new PaymentProviders(conf, client, environment.getObjectMapper());
        CardService cardService = new CardService(gatewayAccountDao, chargeDao, providers);

        environment.jersey().register(new SecurityTokensResource(tokenDao));
        environment.jersey().register(new NotificationResource(providers, chargeDao));
        environment.jersey().register(new ChargesApiResource(chargeDao, tokenDao, gatewayAccountDao, conf.getLinks()));
        environment.jersey().register(new ChargesFrontendResource(chargeDao, gatewayAccountDao));
        environment.jersey().register(new CardResource(cardService));
        environment.jersey().register(new GatewayAccountResource(gatewayAccountDao));

        environment.jersey().register(

                AuthFactory.binder(
                new BasicAuthFactory<>(
                        new SmartpayAuthenticator(
                                conf.getSmartpayConfig().getNotification()),
                        "",
                        String.class)));

        environment.healthChecks().register("database", new DatabaseHealthCheck(jdbi, dataSourceFactory.getValidationQuery()));
    }

    private Client createJerseyClient(Environment environment, ConnectorConfiguration conf) {
        JerseyClientConfiguration clientConfiguration = conf.getClientConfiguration();
        ApacheConnectorProvider connectorProvider = new ApacheConnectorProvider();

        Duration readTimeout = conf.getCustomJerseyClient().getReadTimeout();
        int readTimeoutInMillis = (int)(readTimeout.toMilliseconds());

        return new JerseyClientBuilder(environment)
                .using(connectorProvider)
                .using(clientConfiguration)
                .withProperty(ClientProperties.READ_TIMEOUT, readTimeoutInMillis)
                .build(getName());
    }

    public DBI getJdbi() {
        return jdbi;
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }
}
