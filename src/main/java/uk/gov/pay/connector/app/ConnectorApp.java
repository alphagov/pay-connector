package uk.gov.pay.connector.app;

import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.bundles.DBIExceptionsBundle;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;
import uk.gov.pay.connector.resources.*;
import uk.gov.pay.connector.service.CardService;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.util.DbConnectionChecker;

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
        PaymentProviders providers = new PaymentProviders(conf);
        CardService cardService = new CardService(gatewayAccountDao, chargeDao, providers);

        environment.jersey().register(new SecurityTokensResource(tokenDao));
        environment.jersey().register(new ChargesApiResource(chargeDao, tokenDao, gatewayAccountDao, conf.getLinks()));
        environment.jersey().register(new ChargesFrontendResource(chargeDao, gatewayAccountDao));
        environment.jersey().register(new CardResource(cardService));
        environment.jersey().register(new GatewayAccountResource(gatewayAccountDao));

        environment.healthChecks().register("database", new DatabaseHealthCheck(jdbi, dataSourceFactory.getValidationQuery()));
    }

    public DBI getJdbi() {
        return jdbi;
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }
}
