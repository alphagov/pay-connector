package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface WorldpayAuthoriseRequestWithOptionalEmail extends WorldpayAuthoriseRequest {
    
    @Nullable String email();

}
