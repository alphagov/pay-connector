package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsSourceResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeParamsFor3ds;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentConfirmationResponse;

import java.util.Optional;

public class Stripe3dsSourceAuthorisationResponse implements BaseAuthoriseResponse {

    private StripePaymentIntentConfirmationResponse jsonResponse;


    Stripe3dsSourceAuthorisationResponse(StripePaymentIntentConfirmationResponse jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    @Override
    public String getTransactionId() {
        return jsonResponse.getId();
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        return AuthoriseStatus.REQUIRES_3DS;
    }

    @Override
    public Optional<? extends GatewayParamsFor3ds> getGatewayParamsFor3ds() {
        return Optional.of(new StripeParamsFor3ds(jsonResponse.getRedirectUrl()));
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
