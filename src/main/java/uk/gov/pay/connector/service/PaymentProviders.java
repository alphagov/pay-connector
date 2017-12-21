package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.service.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.service.epdq.EpdqSha512SignatureGenerator;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import java.util.EnumMap;
import java.util.function.BiFunction;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;
import static uk.gov.pay.connector.service.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.service.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.service.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.service.GatewayOperation.REFUND;
import static uk.gov.pay.connector.service.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.service.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.service.PaymentGatewayName.WORLDPAY;

/**
 * TODO: Currently, the usage of this class at runtime is a single instance instantiated by ConnectorApp.
 * - In this instance we are creating 3 instances for each provider which internally holds an instance of a GatewayClient
 * - Due to this all calls to a particular gateway goes via this single instance.
 * - We are currently not sure of the state in Dropwizard's Jersey Client wrapper and if so this may lead to multi-threading issues
 * - Potential refactoring after a performance test
 */
public class PaymentProviders {

    private final GatewayClientFactory gatewayClientFactory;
    private final Environment environment;
    private final ConnectorConfiguration config;
    private final ExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
    private final ExternalRefundAvailabilityCalculator epdqExternalRefundAvailabilityCalculator = new EpdqExternalRefundAvailabilityCalculator();
    private final WorldpayPaymentProvider worldpayProvider;
    private final SmartpayPaymentProvider smartPayProvider;
    private final SandboxPaymentProvider sandboxProvider;
    private final EpdqPaymentProvider epdqProvider;

    @Inject
    public PaymentProviders(ConnectorConfiguration config, GatewayClientFactory gatewayClientFactory, ObjectMapper objectMapper, Environment environment) {
        this.gatewayClientFactory = gatewayClientFactory;
        this.environment = environment;
        this.config = config;

        worldpayProvider = createWorldpayProvider();
        smartPayProvider = createSmartPayProvider(objectMapper);
        sandboxProvider = new SandboxPaymentProvider(defaultExternalRefundAvailabilityCalculator);
        epdqProvider = createEpdqProvider();
    }

    private GatewayClient gatewayClientForOperation(PaymentGatewayName gateway,
                                                    GatewayOperation operation,
                                                    BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> sessionIdentifier) {
        return gatewayClientFactory.createGatewayClient(
                gateway, operation, config.getGatewayConfigFor(gateway).getUrls(), sessionIdentifier, environment.metrics()
        );
    }

    private WorldpayPaymentProvider createWorldpayProvider() {
        EnumMap<GatewayOperation, GatewayClient> gatewayClientEnumMap = GatewayOperationClientBuilder.builder()
                .authClient(gatewayClientForOperation(WORLDPAY, AUTHORISE, WorldpayPaymentProvider.includeSessionIdentifier()))
                .cancelClient(gatewayClientForOperation(WORLDPAY, CANCEL, WorldpayPaymentProvider.includeSessionIdentifier()))
                .captureClient(gatewayClientForOperation(WORLDPAY, CAPTURE, WorldpayPaymentProvider.includeSessionIdentifier()))
                .refundClient(gatewayClientForOperation(WORLDPAY, REFUND, WorldpayPaymentProvider.includeSessionIdentifier()))
                .build();

        WorldpayConfig worldpayConfig = config.getWorldpayConfig();

        return new WorldpayPaymentProvider(
                gatewayClientEnumMap,worldpayConfig.isSecureNotificationEnabled(),worldpayConfig.getNotificationDomain(), defaultExternalRefundAvailabilityCalculator);
    }

    private SmartpayPaymentProvider createSmartPayProvider(ObjectMapper objectMapper) {
        EnumMap<GatewayOperation, GatewayClient> gatewayClients = GatewayOperationClientBuilder
                .builder()
                .authClient(gatewayClientForOperation(SMARTPAY, AUTHORISE, SmartpayPaymentProvider.includeSessionIdentifier()))
                .captureClient(gatewayClientForOperation(SMARTPAY, CAPTURE, SmartpayPaymentProvider.includeSessionIdentifier()))
                .cancelClient(gatewayClientForOperation(SMARTPAY, CANCEL, SmartpayPaymentProvider.includeSessionIdentifier()))
                .refundClient(gatewayClientForOperation(SMARTPAY, REFUND, SmartpayPaymentProvider.includeSessionIdentifier()))
                .build();

        return new SmartpayPaymentProvider(
                gatewayClients,
                objectMapper,
                defaultExternalRefundAvailabilityCalculator
        );
    }

    private EpdqPaymentProvider createEpdqProvider() {
        EnumMap<GatewayOperation, GatewayClient> gatewayClientEnumMap = GatewayOperationClientBuilder.builder()
                .authClient(gatewayClientForOperation(EPDQ, AUTHORISE, EpdqPaymentProvider.includeSessionIdentifier()))
                .cancelClient(gatewayClientForOperation(EPDQ, CANCEL, EpdqPaymentProvider.includeSessionIdentifier()))
                .captureClient(gatewayClientForOperation(EPDQ, CAPTURE, EpdqPaymentProvider.includeSessionIdentifier()))
                .refundClient(gatewayClientForOperation(EPDQ, REFUND, EpdqPaymentProvider.includeSessionIdentifier()))
                .build();

        return new EpdqPaymentProvider(gatewayClientEnumMap, new EpdqSha512SignatureGenerator(), epdqExternalRefundAvailabilityCalculator);
    }

    public WorldpayPaymentProvider getWorldpayProvider() {
        return worldpayProvider;
    }

    public <R> PaymentProviderNotificationHandler<R> notificationHandler(PaymentGatewayName gateway) {
        switch(gateway) {
            case SMARTPAY:
                PaymentProviderNotificationHandler<R> s = (PaymentProviderNotificationHandler<R>) this.smartPayProvider;
                return s;
            case SANDBOX:
                return (PaymentProviderNotificationHandler<R>) sandboxProvider;
            case EPDQ:
                return (PaymentProviderNotificationHandler<R>) epdqProvider;
            case WORLDPAY:
                return (PaymentProviderNotificationHandler<R>) worldpayProvider;
            default:
                throw new RuntimeException("Unknown PaymentGatewayName " + gateway.getName());
        }
    }

    public PaymentProviderOperations byName(PaymentGatewayName gateway) {
        switch(gateway) {
            case SMARTPAY:
                return smartPayProvider;
            case SANDBOX:
                return sandboxProvider;
            case EPDQ:
                return epdqProvider;
            default:
                throw new RuntimeException("Unknown PaymentGatewayName " + gateway.getName());
        }
    }
}
