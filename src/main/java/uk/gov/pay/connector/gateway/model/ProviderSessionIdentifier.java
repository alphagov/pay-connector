package uk.gov.pay.connector.gateway.model;

import uk.gov.service.payments.commons.model.WrappedStringValue;

/**
 * A session identifier that a payment provider requires us to store
 * and send to back to them, for example Worldpay’s “machine” cookie,
 * which we receive when Worldpay tells us 3D Secure is required and
 * send back to them with the 3D Secure result
 */
public class ProviderSessionIdentifier extends WrappedStringValue {

    private ProviderSessionIdentifier(String value) {
        super(value);
    }
    
    public static ProviderSessionIdentifier of(String providerSessionIdentifier) {
        return new ProviderSessionIdentifier(providerSessionIdentifier);
    }
}
