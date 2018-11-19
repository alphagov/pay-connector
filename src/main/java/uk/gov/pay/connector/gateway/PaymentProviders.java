package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.applepay.ApplePayAuthoriser;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import java.util.Map;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;

public class PaymentProviders {

    private final Map<PaymentGatewayName, PaymentProvider> cardPaymentProviders = newHashMap();
    private final Map<PaymentGatewayName, ApplePayAuthoriser> applePayPaymentProviders = newHashMap();
    
    @Inject
    public PaymentProviders(WorldpayPaymentProvider worldpayPaymentProvider,
                            EpdqPaymentProvider epdqPaymentProvider,
                            SmartpayPaymentProvider smartpayPaymentProvider,
                            SandboxPaymentProvider sandboxPaymentProvider,
                            StripePaymentProvider stripePaymentProvider) {
        cardPaymentProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        cardPaymentProviders.put(PaymentGatewayName.SANDBOX, sandboxPaymentProvider);
        cardPaymentProviders.put(PaymentGatewayName.SMARTPAY, smartpayPaymentProvider);
        cardPaymentProviders.put(PaymentGatewayName.EPDQ, epdqPaymentProvider);
        cardPaymentProviders.put(PaymentGatewayName.STRIPE, stripePaymentProvider);

        applePayPaymentProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        applePayPaymentProviders.put(PaymentGatewayName.SANDBOX, sandboxPaymentProvider);
    }

    public PaymentProvider byName(PaymentGatewayName gateway) {
        return cardPaymentProviders.get(gateway);
    }
    
    public ApplePayAuthoriser getAppleAuthoriserFor(ChargeEntity chargeEntity) {
        return applePayPaymentProviders.get(chargeEntity.getPaymentGatewayName());
    }

}
