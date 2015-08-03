package uk.gov.pay.connector.app;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.bundles.DBIExceptionsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.healthcheck.DatabaseHealthCheck;
import uk.gov.pay.connector.healthcheck.Ping;

import java.util.function.Supplier;

public class ConnectorApp extends Application<ConnectorConfiguration> {

    @Override
    public void initialize(Bootstrap<ConnectorConfiguration> bootstrap) {
        // Enable variable substitution with environment variables
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor()
                )
        );
        // Unwrap SQLException and DBIException instances:
        bootstrap.addBundle(new DBIExceptionsBundle());
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }

    @Override
    public void run(ConnectorConfiguration configuration, Environment environment) throws Exception {
        environment.healthChecks().register("ping", new Ping());
        final DBIFactory factory = new DBIFactory();
        final DBI jdbi = factory.build(environment, configuration.getDataSourceFactory(), "postgresql");
        environment.healthChecks().register("database", new DatabaseHealthCheck(jdbi, configuration.getDataSourceFactory().getValidationQuery()));
    }
}
