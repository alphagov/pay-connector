package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface Worldpay3dsFlexEligibleAuthoriseRequest extends Worldpay3dsEligibleAuthoriseRequest {

    @Nullable String dfReferenceId();

    @Override
    default boolean is3dsFlexRequest() {
        return true;
    }

}
