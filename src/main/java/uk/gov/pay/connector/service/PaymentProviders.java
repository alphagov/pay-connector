package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.app.WorldpayNotificationConfig;
import uk.gov.pay.connector.resources.PaymentGatewayName;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import java.util.Map;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;
import static uk.gov.pay.connector.resources.PaymentGatewayName.*;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;

/**
 * TODO: Currently, the usage of this class at runtime is a single instance instantiated by ConnectorApp.
 * - In this instance we are creating 3 instances for each provider which internally holds an instance of a GatewayClient
 * - Due to this all calls to a particular gateway goes via this single instance.
 * - We are currently not sure of the state in Dropwizard's Jersey Client wrapper and if so this may lead to multi-threading issues
 * - Potential refactoring after a performance test
 */
public class PaymentProviders<T extends BaseResponse> {

    private final Map<PaymentGatewayName, PaymentProvider> paymentProviders = newHashMap();

    @Inject
    public PaymentProviders(ConnectorConfiguration config, ClientFactory clientFactory, ObjectMapper objectMapper) {
        this.paymentProviders.put(WORLDPAY, createWorldpayProvider(clientFactory, config.getWorldpayConfig()));
        this.paymentProviders.put(SMARTPAY, createSmartPayProvider(clientFactory, config.getSmartpayConfig(), objectMapper));
        this.paymentProviders.put(SANDBOX, new SandboxPaymentProvider());
    }

    private PaymentProvider createWorldpayProvider(ClientFactory clientFactory,
                                                   GatewayCredentialsConfig config) {
        return new WorldpayPaymentProvider(
                createGatewayClient(clientFactory.createWithDropwizardClient("WORLD_PAY"), config.getUrls()),
                ((WorldpayNotificationConfig) config).isSecureNotificationEnabled(),
                ((WorldpayNotificationConfig) config).getNotificationDomain()
        );
    }

    private PaymentProvider createSmartPayProvider(ClientFactory clientFactory,
                                                   GatewayCredentialsConfig config,
                                                   ObjectMapper objectMapper) {
        return new SmartpayPaymentProvider(
                createGatewayClient(clientFactory.createWithDropwizardClient("SMART_PAY"), config.getUrls()),
                objectMapper
        );
    }

    public PaymentProvider<T> byName(PaymentGatewayName name) {
        return paymentProviders.get(name);
    }
}
