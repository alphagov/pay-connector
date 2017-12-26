package uk.gov.pay.connector.provider.epdq;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.provider.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.service.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.service.epdq.EpdqSha512SignatureGenerator;
import uk.gov.pay.connector.service.epdq.SignatureGenerator;

import javax.inject.Named;

public class EpdqModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EpdqPaymentProvider.class).in(Singleton.class);
        bind(SignatureGenerator.class).to(EpdqSha512SignatureGenerator.class);
    }

    @Provides
    @Named("EPDQ")
    GatewayConfig epdqConfig(ConnectorConfiguration config) {
        return config.getEpdqConfig();
    }
}
