package uk.gov.pay.connector.service;


import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static uk.gov.pay.connector.resources.PaymentProviderValidator.DEFAULT_PROVIDER;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;


public class PaymentProviderFactory {

    private static final Map<String, PaymentProvider> providers = new HashMap<String, PaymentProvider>() {{
        put(WORLDPAY_PROVIDER, new WorldpayPaymentProvider());
        put(DEFAULT_PROVIDER, new SandboxPaymentProvider());
    }};

    public static Optional<PaymentProvider> resolve(String paymentProviderName) {
        return Optional.ofNullable(providers.get(paymentProviderName));
    }
}
