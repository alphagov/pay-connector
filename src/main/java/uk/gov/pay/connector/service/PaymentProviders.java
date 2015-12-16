package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import static java.lang.String.format;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.*;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;

/**
 * TODO: Currently, the usage of this class at runtime is a single instance instantiated by ConnectorApp.
 *      - In this instance we are creating 3 instances for each provider which internally holds an instance of a GatewayClient
 *      - Due to this all calls to a particular gateway goes via this single instance.
 *      - We are currently not sure of the state in Dropwizard's Jersey Client wrapper and if so this may lead to multi-threading issues
 *      - Potential refactoring after a performance test
 */
public class PaymentProviders {
    private final PaymentProvider worldpayProvider;
    private final PaymentProvider smartpayProvider;
    private final PaymentProvider sandboxProvider;

    public PaymentProviders(ConnectorConfiguration config, ClientFactory clientFactory, ObjectMapper objectMapper) {
        this.worldpayProvider = createWorldpayProvider(clientFactory, config.getWorldpayConfig());
        this.smartpayProvider = createSmartPayProvider(clientFactory, config.getSmartpayConfig(), objectMapper);
        this.sandboxProvider = new SandboxPaymentProvider(objectMapper);
    }

    private PaymentProvider createWorldpayProvider(ClientFactory clientFactory,
                                                   GatewayCredentialsConfig config) {
        return new WorldpayPaymentProvider(
                createGatewayClient(clientFactory.createWithDropwizardClient("WORLD_PAY"), config.getUrl())
        );
    }

    private PaymentProvider createSmartPayProvider(ClientFactory clientFactory,
                                                   GatewayCredentialsConfig config,
                                                   ObjectMapper objectMapper) {
        return new SmartpayPaymentProvider(
                createGatewayClient(clientFactory.createWithDropwizardClient("SMART_PAY"), config.getUrl()),
                objectMapper
        );
    }

    public PaymentProvider resolve(String paymentProviderName) {
        switch (paymentProviderName) {
            case WORLDPAY_PROVIDER:
                return worldpayProvider;
            case SMARTPAY_PROVIDER:
                return smartpayProvider;
            case DEFAULT_PROVIDER:
                return sandboxProvider;
            default:
                throw new RuntimeException(format("Unsupported PaymentProvider %s", paymentProviderName));
        }
    }
}
