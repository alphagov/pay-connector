package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface WorldpayRecurringCustomerInitiatedAuthoriseRequest extends WorldpayAuthoriseRequestWithOptionalEmail {

    @Nullable String email();
    String authenticatedShopperId();
    String tokenEventReference();
    @Nullable String customerInitiatedReason();

    @Override
    default boolean storesTokenisedCredentials() {
        return true;
    }

}
