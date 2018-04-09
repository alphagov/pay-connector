package uk.gov.pay.connector.notification.worldpay;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;

public class WorldpayModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(WorldpayPaymentProvider.class).in(Singleton.class);
    }

    @Provides
    WorldpayConfig worldpayConfig(ConnectorConfiguration config) {
        return config.getWorldpayConfig();
    }
}
