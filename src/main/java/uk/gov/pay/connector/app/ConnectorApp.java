package uk.gov.pay.connector.app;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.healthcheck.Ping;

public class ConnectorApp extends Application<Configuration> {

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        // Enable variable substitution with environment variables
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor()
                )
        );
    }

    public static void main(String[] args) throws Exception {
        new ConnectorApp().run(args);
    }

    @Override
    public void run(Configuration configuration, Environment environment) throws Exception {
         environment.healthChecks().register("ping", new Ping());
    }
}
