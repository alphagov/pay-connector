package uk.gov.pay.connector.rules;

import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import org.junit.rules.TestRule;

public interface AppRule<C extends Configuration> extends TestRule {
    C getConfiguration();

    int getLocalPort();

    int getPort(int connectorIndex);

    int getAdminPort();

    <A extends Application<C>> A getApplication();

    Environment getEnvironment();

    Injector getInjector();

    <T> T getInstanceFromGuiceContainer(Class<T> type);
}
