package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.commons.model.WrappedStringValue;

/**
 * A unique session ID that Worldpay requires us to send with a request to
 * authorise a payment (we usually use the payment’s own external ID)
 */
public class WorldpayAuthoriseOrderSessionId extends WrappedStringValue {
    
    private WorldpayAuthoriseOrderSessionId(String worldpayAuthoriseOrderSessionId) {
        super(worldpayAuthoriseOrderSessionId);
    }
    
    public static WorldpayAuthoriseOrderSessionId of(String worldpayAuthoriseOrderSessionId) {
        return new WorldpayAuthoriseOrderSessionId(worldpayAuthoriseOrderSessionId);
    }
}
