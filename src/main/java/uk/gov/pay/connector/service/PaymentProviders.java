package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;
import static uk.gov.pay.connector.service.GatewayOperation.*;
import static uk.gov.pay.connector.service.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.service.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.service.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider.includeSessionIdentifier;

/**
 * TODO: Currently, the usage of this class at runtime is a single instance instantiated by ConnectorApp.
 * - In this instance we are creating 3 instances for each provider which internally holds an instance of a GatewayClient
 * - Due to this all calls to a particular gateway goes via this single instance.
 * - We are currently not sure of the state in Dropwizard's Jersey Client wrapper and if so this may lead to multi-threading issues
 * - Potential refactoring after a performance test
 */
public class PaymentProviders<T extends BaseResponse> {

    private final Map<PaymentGatewayName, PaymentProvider> paymentProviders = newHashMap();
    private final GatewayClientFactory gatewayClientFactory;
    private final Environment environment;
    private final ConnectorConfiguration config;

    @Inject
    public PaymentProviders(ConnectorConfiguration config, GatewayClientFactory gatewayClientFactory, ObjectMapper objectMapper, Environment environment) {
        this.gatewayClientFactory = gatewayClientFactory;
        this.environment = environment;
        this.config = config;

        this.paymentProviders.put(WORLDPAY, createWorldpayProvider());
        this.paymentProviders.put(SMARTPAY, createSmartPayProvider(objectMapper));
        this.paymentProviders.put(SANDBOX, new SandboxPaymentProvider());
    }

    private PaymentProvider createWorldpayProvider() {
        EnumMap<GatewayOperation, GatewayClient> gatewayClientEnumMap = GatewayOperationClientBuilder.builder()
                .authClient(gatewayClientForOperation(WORLDPAY, AUTHORISE, includeSessionIdentifier()))
                .cancelClient(gatewayClientForOperation(WORLDPAY, CANCEL, includeSessionIdentifier()))
                .captureClient(gatewayClientForOperation(WORLDPAY, CAPTURE, includeSessionIdentifier()))
                .refundClient(gatewayClientForOperation(WORLDPAY, REFUND, includeSessionIdentifier()))
                .build();

        WorldpayConfig worldpayConfig = config.getWorldpayConfig();

        return new WorldpayPaymentProvider(
                gatewayClientEnumMap,worldpayConfig.isSecureNotificationEnabled(),worldpayConfig.getNotificationDomain());
    }

    private GatewayClient gatewayClientForOperation(PaymentGatewayName gateway,
                                                    GatewayOperation operation,
                                                    BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> sessionIdentifier) {
        return gatewayClientFactory.createGatewayClient(
                gateway, operation, config.getGatewayConfigFor(gateway).getUrls(),
                MediaType.APPLICATION_XML_TYPE, sessionIdentifier,
                environment.metrics());
    }

    private PaymentProvider createSmartPayProvider(ObjectMapper objectMapper) {
        EnumMap<GatewayOperation, GatewayClient> gatewayClients = GatewayOperationClientBuilder
                .builder()
                .authClient(gatewayClientForOperation(SMARTPAY, AUTHORISE, SmartpayPaymentProvider.includeSessionIdentifier()))
                .captureClient(gatewayClientForOperation(SMARTPAY, CAPTURE, SmartpayPaymentProvider.includeSessionIdentifier()))
                .cancelClient(gatewayClientForOperation(SMARTPAY, CANCEL, SmartpayPaymentProvider.includeSessionIdentifier()))
                .refundClient(gatewayClientForOperation(SMARTPAY, REFUND, SmartpayPaymentProvider.includeSessionIdentifier()))
                .build();

        return new SmartpayPaymentProvider(
                gatewayClients,
                objectMapper
        );
    }

    public PaymentProvider<T> byName(PaymentGatewayName gateway) {
        return paymentProviders.get(gateway);
    }
}
