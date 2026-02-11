package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface Worldpay3dsEligibleAuthoriseRequest extends WorldpayAuthoriseRequest {

    @Nullable String email();
    @Nullable String ipAddress();
    String acceptHeader();
    String userAgentHeader();
    String sessionId();

}
