package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;

import jakarta.inject.Inject;

public class StripeSdkClientFactory {

    private final StripeGatewayConfig stripeGatewayConfig;
    private final StripeSdkWrapper stripeSdkWrapper;

    @Inject
    public StripeSdkClientFactory(ConnectorConfiguration configuration, StripeSdkWrapper stripeSdkWrapper) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.stripeSdkWrapper = stripeSdkWrapper;
    }

    public StripeSdkClient getInstance() {
        return newInstance(stripeGatewayConfig, stripeSdkWrapper);
    }

    private StripeSdkClient newInstance(StripeGatewayConfig stripeGatewayConfig, StripeSdkWrapper stripeSdkWrapper) {
        return new StripeSdkClient(stripeGatewayConfig, stripeSdkWrapper);
    }

}
