package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface WorldpayAuthoriseRequestWithOptionalIpAddress {
    
    @Nullable String ipAddress();

}
