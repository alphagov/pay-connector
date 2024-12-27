package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import jakarta.inject.Inject;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class PaymentProviders {

    private final Map<PaymentGatewayName, PaymentProvider> paymentProviders = newHashMap();
    
    @Inject
    public PaymentProviders(WorldpayPaymentProvider worldpayPaymentProvider,
                            SandboxPaymentProvider sandboxPaymentProvider,
                            StripePaymentProvider stripePaymentProvider) {
        paymentProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        paymentProviders.put(PaymentGatewayName.SANDBOX, sandboxPaymentProvider);
        paymentProviders.put(PaymentGatewayName.STRIPE, stripePaymentProvider);

    }

    public PaymentProvider byName(PaymentGatewayName gateway) {
        return paymentProviders.get(gateway);
    }
}
