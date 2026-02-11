package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface WorldpayAuthoriseRequestWithOptional3dsExemption extends Worldpay3dsEligibleAuthoriseRequest permits
        WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequest,
        WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequest,
        WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest,
        WorldpayRecurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequest {

    @Nullable WorldpayExemptionRequest exemption();

}
