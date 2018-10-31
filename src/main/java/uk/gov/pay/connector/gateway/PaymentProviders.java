package uk.gov.pay.connector.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
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
    private final ExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();

    @Inject
    public PaymentProviders(WorldpayPaymentProvider worldpayPaymentProvider,
                            EpdqPaymentProvider epdqPaymentProvider, 
                            SmartpayPaymentProvider smartpayPaymentProvider) {
        this.paymentProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        this.paymentProviders.put(PaymentGatewayName.SMARTPAY, smartpayPaymentProvider);
        this.paymentProviders.put(PaymentGatewayName.SANDBOX, new SandboxPaymentProvider(defaultExternalRefundAvailabilityCalculator));
        this.paymentProviders.put(PaymentGatewayName.EPDQ, epdqPaymentProvider);
    }

    public PaymentProvider<T, ?> byName(PaymentGatewayName gateway) {
        return paymentProviders.get(gateway);
    }
}
