package uk.gov.pay.connector.rules;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.rules.ExternalResource;
import uk.gov.pay.connector.app.InjectorLookup;

import javax.annotation.Nullable;
import java.util.Enumeration;

/**
 * A JUnit rule for starting and stopping your guice application at the start and end of a test class.
 * <p>
 * <p>Based on {@link io.dropwizard.testing.junit.DropwizardAppRule}, but doesn't start Jetty.
 * Emulates managed objects lifecycle.</p>
 * <p>Supposed to be used for testing internal services business logic as lightweight alternative for
 * dropwizard rule.</p>
 */
public class GuiceAppRule<C extends Configuration> extends ExternalResource implements AppRule<C> {

    private final Class<? extends Application<C>> applicationClass;
    private final String configPath;

    private C configuration;
    private Application<C> application;
    private Environment environment;
    private TestCommand<C> command;

    public GuiceAppRule(final Class<? extends Application<C>> applicationClass,
                        @Nullable final String configPath,
                        final ConfigOverride... configOverrides) {
        this.applicationClass = applicationClass;
        this.configPath = configPath;
        for (ConfigOverride configOverride : configOverrides) {
            configOverride.addToSystemProperties();
        }
    }

    @Override
    public C getConfiguration() {
        return configuration;
    }

    @Override
    public int getLocalPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPort(int connectorIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getAdminPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Application<C>> A getApplication() {
        return (A) application;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public Injector getInjector() {
        return InjectorLookup.getInjector(application).get();
    }

    @Override
    public <T> T getInstanceFromGuiceContainer(final Class<T> type) {
        return getInjector().getInstance(type);
    }

    protected Application<C> newApplication() {
        try {
            return applicationClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate application", e);
        }
    }

    @Override
    protected void before() {
        startIfRequired();
    }

    @Override
    protected void after() {
        resetConfigOverrides();
        command.stop();
        command = null;
    }

    private void startIfRequired() {
        if (command != null) {
            return;
        }

        try {
            application = newApplication();

            final Bootstrap<C> bootstrap = new Bootstrap<C>(application) {
                @Override
                public void run(final C configuration, final Environment environment) throws Exception {
                    GuiceAppRule.this.configuration = configuration;
                    GuiceAppRule.this.environment = environment;
                    super.run(configuration, environment);
                }
            };

            application.initialize(bootstrap);

            startCommand(bootstrap);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to start test environment", e);
        }
    }

    private void startCommand(final Bootstrap<C> bootstrap) throws Exception {
        command = new TestCommand<>(application);

        final ImmutableMap.Builder<String, Object> file = ImmutableMap.builder();
        if (!Strings.isNullOrEmpty(configPath)) {
            file.put("file", configPath);
        }
        final Namespace namespace = new Namespace(file.build());

        command.run(bootstrap, namespace);
    }

    private void resetConfigOverrides() {
        for (final Enumeration<?> props = System.getProperties().propertyNames(); props.hasMoreElements(); ) {
            final String keyString = (String) props.nextElement();
            if (keyString.startsWith("dw.")) {
                System.clearProperty(keyString);
            }
        }
    }
}
