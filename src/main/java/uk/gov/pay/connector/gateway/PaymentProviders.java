package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import java.util.Map;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;

public class PaymentProviders {

    private final Map<PaymentGatewayName, PaymentProvider> paymentProviders = newHashMap();
    
    @Inject
    public PaymentProviders(WorldpayPaymentProvider worldpayPaymentProvider,
                            EpdqPaymentProvider epdqPaymentProvider,
                            SmartpayPaymentProvider smartpayPaymentProvider,
                            SandboxPaymentProvider sandboxPaymentProvider,
                            StripePaymentProvider stripePaymentProvider) {
        paymentProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        paymentProviders.put(PaymentGatewayName.SANDBOX, sandboxPaymentProvider);
        paymentProviders.put(PaymentGatewayName.SMARTPAY, smartpayPaymentProvider);
        paymentProviders.put(PaymentGatewayName.EPDQ, epdqPaymentProvider);
        paymentProviders.put(PaymentGatewayName.STRIPE, stripePaymentProvider);

    }

    public PaymentProvider byName(PaymentGatewayName gateway) {
        return paymentProviders.get(gateway);
    }
}
