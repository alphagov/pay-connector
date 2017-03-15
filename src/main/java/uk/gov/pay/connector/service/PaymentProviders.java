package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.resources.PaymentGatewayName;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.util.EnumMap;
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
    public PaymentProviders(ConnectorConfiguration config, ClientFactory clientFactory, ObjectMapper objectMapper, Environment environment) {

        this.paymentProviders.put(WORLDPAY, createWorldpayProvider(clientFactory, config.getWorldpayConfig(), environment.metrics()));
        this.paymentProviders.put(SMARTPAY, createSmartPayProvider(clientFactory, config.getSmartpayConfig(), objectMapper, environment.metrics()));
        this.paymentProviders.put(SANDBOX, new SandboxPaymentProvider());
    }

    private PaymentProvider createWorldpayProvider(ClientFactory clientFactory,
                                                   GatewayConfig config,
                                                   MetricRegistry metricRegistry) {
        GatewayClient gatewayClient = createGatewayClient(
                clientFactory.createWithDropwizardClient(
                        "WORLD_PAY"), config.getUrls(), MediaType.APPLICATION_XML_TYPE,
                WorldpayPaymentProvider.includeSessionIdentifier(), metricRegistry);

        EnumMap<GatewayOperation, GatewayClient> gatewayClientEnumMap = GatewayOperationClientBuilder.builder()
                .authClient(gatewayClient)
                .cancelClient(gatewayClient)
                .captureClient(gatewayClient)
                .refundClient(gatewayClient).build();

        return new WorldpayPaymentProvider(
                gatewayClientEnumMap,
                ((WorldpayConfig) config).isSecureNotificationEnabled(),
                ((WorldpayConfig) config).getNotificationDomain()
        );

    }

    private PaymentProvider createSmartPayProvider(ClientFactory clientFactory,
                                                   GatewayConfig config,
                                                   ObjectMapper objectMapper,
                                                   MetricRegistry metricRegistry) {
        GatewayClient gatewayClient = createGatewayClient(clientFactory.createWithDropwizardClient(
                "SMART_PAY"), config.getUrls(), MediaType.APPLICATION_XML_TYPE,
                SmartpayPaymentProvider.includeSessionIdentifier(), metricRegistry);

        EnumMap<GatewayOperation, GatewayClient> gatewayClients = GatewayOperationClientBuilder.builder()
                .authClient(gatewayClient)
                .captureClient(gatewayClient)
                .cancelClient(gatewayClient)
                .refundClient(gatewayClient)
                .build();

        return new SmartpayPaymentProvider(
                gatewayClients,
                objectMapper
        );
    }

    public PaymentProvider<T> byName(PaymentGatewayName name) {
        return paymentProviders.get(name);
    }
}
