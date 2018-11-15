package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeCreateChargeResponse;

import java.util.Optional;

import static java.lang.String.format;

public class StripeAuthorisationResponse implements BaseAuthoriseResponse {

    private StripeCreateChargeResponse jsonResponse;

    public StripeAuthorisationResponse(StripeCreateChargeResponse jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    @Override
    public String getTransactionId() {
        return jsonResponse.getTransactionId();
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        String stripeStatus = jsonResponse.getStatus();
        switch (stripeStatus) {
            case "succeeded": 
                return AuthoriseStatus.AUTHORISED;
            default: throw new IllegalArgumentException(format("Cannot map stripe status of %s to an %s", stripeStatus, AuthoriseStatus.class.getName()));
        }
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
