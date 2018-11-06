package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import java.util.Map;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;

public class PaymentProviderFactory {

    private final Map<PaymentGatewayName, PaymentProvider> paymentProviders = newHashMap();

    @Inject
    public PaymentProviderFactory(WorldpayPaymentProvider worldpayPaymentProvider,
                                  EpdqPaymentProvider epdqPaymentProvider,
                                  SmartpayPaymentProvider smartpayPaymentProvider,
                                  SandboxPaymentProvider sandboxPaymentProvider) {
        this.paymentProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        this.paymentProviders.put(PaymentGatewayName.SMARTPAY, smartpayPaymentProvider);
        this.paymentProviders.put(PaymentGatewayName.SANDBOX, sandboxPaymentProvider);
        this.paymentProviders.put(PaymentGatewayName.EPDQ, epdqPaymentProvider);
    }

    public PaymentProvider byName(PaymentGatewayName gateway) {
        return paymentProviders.get(gateway);
    }
}
