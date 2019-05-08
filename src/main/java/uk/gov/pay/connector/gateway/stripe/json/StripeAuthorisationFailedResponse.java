package uk.gov.pay.connector.gateway.stripe.json;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Optional;

public class StripeAuthorisationFailedResponse implements BaseAuthoriseResponse {

    private StripeErrorResponse errorResponse;

    private StripeAuthorisationFailedResponse(StripeErrorResponse errorResponse) {
        this.errorResponse = errorResponse;
    }

    public static StripeAuthorisationFailedResponse of(StripeErrorResponse stripeErrorResponse) {
        return new StripeAuthorisationFailedResponse(stripeErrorResponse);
    }

    @Override
    public String getTransactionId() {
        return errorResponse.getError().getCharge();
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        return AuthoriseStatus.REJECTED;
    }

    @Override
    public Optional<? extends GatewayParamsFor3ds> getGatewayParamsFor3ds() {
        return Optional.empty();
    }

    @Override
    public String getErrorCode() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }
}
