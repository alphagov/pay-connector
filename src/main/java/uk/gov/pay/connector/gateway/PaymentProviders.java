package uk.gov.pay.connector.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.epdq.EpdqSha512SignatureGenerator;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.EpdqExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;

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
    private final ExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
    private final ExternalRefundAvailabilityCalculator epdqExternalRefundAvailabilityCalculator = new EpdqExternalRefundAvailabilityCalculator();

    @Inject
    public PaymentProviders(ConnectorConfiguration config, 
                            GatewayClientFactory gatewayClientFactory, 
                            ObjectMapper objectMapper, 
                            Environment environment,
                            WorldpayPaymentProvider worldpayPaymentProvider) {
        this.gatewayClientFactory = gatewayClientFactory;
        this.environment = environment;
        this.config = config;

        this.paymentProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        this.paymentProviders.put(PaymentGatewayName.SMARTPAY, createSmartPayProvider(objectMapper));
        this.paymentProviders.put(PaymentGatewayName.SANDBOX, new SandboxPaymentProvider(defaultExternalRefundAvailabilityCalculator));
        this.paymentProviders.put(PaymentGatewayName.EPDQ, createEpdqProvider());
    }

    private GatewayClient gatewayClientForOperation(PaymentGatewayName gateway,
                                                    GatewayOperation operation,
                                                    BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> sessionIdentifier) {
        return gatewayClientFactory.createGatewayClient(
                gateway, operation, config.getGatewayConfigFor(gateway).getUrls(), sessionIdentifier, environment.metrics()
        );
    }

    private PaymentProvider createSmartPayProvider(ObjectMapper objectMapper) {
        EnumMap<GatewayOperation, GatewayClient> gatewayClients = GatewayOperationClientBuilder
                .builder()
                .authClient(gatewayClientForOperation(PaymentGatewayName.SMARTPAY, GatewayOperation.AUTHORISE, SmartpayPaymentProvider.includeSessionIdentifier()))
                .captureClient(gatewayClientForOperation(PaymentGatewayName.SMARTPAY, GatewayOperation.CAPTURE, SmartpayPaymentProvider.includeSessionIdentifier()))
                .cancelClient(gatewayClientForOperation(PaymentGatewayName.SMARTPAY, GatewayOperation.CANCEL, SmartpayPaymentProvider.includeSessionIdentifier()))
                .refundClient(gatewayClientForOperation(PaymentGatewayName.SMARTPAY, GatewayOperation.REFUND, SmartpayPaymentProvider.includeSessionIdentifier()))
                .build();

        return new SmartpayPaymentProvider(
                gatewayClients,
                objectMapper,
                defaultExternalRefundAvailabilityCalculator
        );
    }

    private PaymentProvider createEpdqProvider() {
        EnumMap<GatewayOperation, GatewayClient> gatewayClientEnumMap = GatewayOperationClientBuilder.builder()
                .authClient(gatewayClientForOperation(PaymentGatewayName.EPDQ, GatewayOperation.AUTHORISE, EpdqPaymentProvider.includeSessionIdentifier()))
                .cancelClient(gatewayClientForOperation(PaymentGatewayName.EPDQ, GatewayOperation.CANCEL, EpdqPaymentProvider.includeSessionIdentifier()))
                .captureClient(gatewayClientForOperation(PaymentGatewayName.EPDQ, GatewayOperation.CAPTURE, EpdqPaymentProvider.includeSessionIdentifier()))
                .refundClient(gatewayClientForOperation(PaymentGatewayName.EPDQ, GatewayOperation.REFUND, EpdqPaymentProvider.includeSessionIdentifier()))
                .build();

        return new EpdqPaymentProvider(gatewayClientEnumMap, new EpdqSha512SignatureGenerator(), epdqExternalRefundAvailabilityCalculator, config.getLinks().getFrontendUrl(), environment.metrics());
    }

    public PaymentProvider<T, ?> byName(PaymentGatewayName gateway) {
        return paymentProviders.get(gateway);
    }
}
