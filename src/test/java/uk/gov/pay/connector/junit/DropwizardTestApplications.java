package uk.gov.pay.connector.junit;

import com.amazonaws.services.sqs.AmazonSQS;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.collect.Sets;
import io.dropwizard.Application;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.InjectorLookup;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Runtime.getRuntime;

/**
 * Runs and hold current Dropwizard Applications running
 *
 * @see <a href="http://www.dropwizard.io/1.2.0/docs/manual/testing.html">http://www.dropwizard.io/1.2.0/docs/manual/testing.html<a/>
 * <p>
 * - Keeps a single instance of a Dropwizard application per configuration values that differs from existing ones,
 * otherwise will create a new instance with the new configuration. So those test classes using {@link DropwizardJUnitRunner}
 * and annotating the configuration similar to other test classes won't create new applications but reuse the first that
 * started up.
 */
final class DropwizardTestApplications {

    private static final Logger logger = LoggerFactory.getLogger(DropwizardTestApplications.class);
    private static final Map<Pair<Class<? extends Application>, String>, DropwizardTestSupport> apps = new ConcurrentHashMap<>();
    private static Set<ConfigOverride> configs = Sets.newHashSet();

    static {
        getRuntime().addShutdownHook(new Thread(() -> {
            for (DropwizardTestSupport applicationsRunning : apps.values()) {
                applicationsRunning.after();
            }
        }));
    }

    static Optional<DropwizardTestSupport> createIfNotRunning(Class<? extends Application> appClass, String configClasspathLocation, ConfigOverride... configOverrides) {
        Pair<Class<? extends Application>, String> key = Pair.of(appClass, configClasspathLocation);

        shutdownIfConfigHasChanged(configOverrides);

        if (!apps.containsKey(key)) {
            try {
                String resourceConfigFilePath = ResourceHelpers.resourceFilePath(configClasspathLocation);
                DropwizardTestSupport newApp = new DropwizardTestSupport(appClass,
                        resourceConfigFilePath,
                        configOverrides);
                apps.put(key, newApp);
                configs = Sets.newHashSet(configOverrides);
                newApp.before();
                return Optional.of(newApp);
            } catch (Exception ex) {
                logger.info(ex.getMessage());
            }

        }
        return Optional.empty();
    }

    private static void shutdownIfConfigHasChanged(ConfigOverride[] configOverrides) {
        var newConfigOverrides = Sets.newHashSet(configOverrides);
        if (!configs.equals(newConfigOverrides)) {
            logger.info("Shutting down dropwizard as config has changed");
            apps.values().forEach(DropwizardTestSupport::after);
            apps.clear();
        } else {
            logger.info("Config was not changed.");
        }
    }

    static TestContext getTestContextOf(Class<? extends Application<?>> appClass, String configClasspathLocation, 
                                        WireMockServer wireMockServer, AmazonSQS amazonSQS) {
        Pair<Class<? extends Application>, String> appConfig = Pair.of(appClass, configClasspathLocation);
        DropwizardTestSupport application = apps.get(appConfig);
        return new TestContext(application.getLocalPort(), ((ConnectorConfiguration) application.getConfiguration()),
                InjectorLookup.getInjector(application.getApplication()).get(), wireMockServer, amazonSQS);
    }

    static void removeConfigOverridesFromSystemProperties() {
        configs.forEach(ConfigOverride::removeFromSystemProperties);
    }
}
