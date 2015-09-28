package uk.gov.pay.connector.resources;

import java.util.List;

import static java.util.Arrays.asList;

public class PaymentProviderValidator {

    public static final String PAYMENT_PROVIDER_KEY = "payment_provider";

    public static final String DEFAULT_PROVIDER = "sandbox";

    public static final String WORLDPAY_PROVIDER = "worldpay";

    public static final String SMARTPAY_PROVIDER = "smartpay";

    public static boolean isValidProvider(String provider) {
        return VALID_PROVIDERS.contains(provider);
    }
    private static final List<String> VALID_PROVIDERS = asList(
            DEFAULT_PROVIDER,
            WORLDPAY_PROVIDER,
            SMARTPAY_PROVIDER
    );
}
