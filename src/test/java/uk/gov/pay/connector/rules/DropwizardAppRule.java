package uk.gov.pay.connector.rules;

import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.testing.ConfigOverride;
import uk.gov.pay.connector.app.InjectorLookup;

import javax.annotation.Nullable;

public class DropwizardAppRule<C extends Configuration> extends io.dropwizard.testing.junit.DropwizardAppRule<C> implements AppRule<C> {

    public DropwizardAppRule(final Class<? extends Application<C>> applicationClass,
                             @Nullable final String configPath,
                             final ConfigOverride... configOverrides) {
        super(applicationClass, configPath, configOverrides);
    }

    @Override
    public Injector getInjector() {
        return InjectorLookup.getInjector(this.getApplication()).get();
    }

    @Override
    public <T> T getInstanceFromGuiceContainer(final Class<T> type) {
        return getInjector().getInstance(type);
    }

}
