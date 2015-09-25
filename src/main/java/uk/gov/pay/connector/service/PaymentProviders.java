package uk.gov.pay.connector.service;


import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.resources.PaymentProviderValidator.DEFAULT_PROVIDER;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;


public class PaymentProviders {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviders(WorldpayConfig config) {
        providers = new HashMap<String, PaymentProvider>() {{
            put(WORLDPAY_PROVIDER, new WorldpayPaymentProvider(config));
            put(DEFAULT_PROVIDER, new SandboxPaymentProvider());
        }};
    }

    public PaymentProvider resolve(String paymentProviderName) {
        return Optional.ofNullable(providers.get(paymentProviderName)).orElseThrow(unsupportedProvider(paymentProviderName));
    }

    private Supplier<RuntimeException> unsupportedProvider(String paymentProviderName) {
        return () -> new RuntimeException("Unsupported PaymentProvider " + paymentProviderName);
    }
}
