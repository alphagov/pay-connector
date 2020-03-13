package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsSourceResponse;

import java.util.Optional;

public class Stripe3dsSourceAuthorisationResponse implements BaseAuthoriseResponse {

    private Stripe3dsSourceResponse jsonResponse;

    public Stripe3dsSourceAuthorisationResponse(Stripe3dsSourceResponse jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    @Override
    public String getTransactionId() {
        return jsonResponse.getId();
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        if ("pending".equals(jsonResponse.getStatus())) {
            return AuthoriseStatus.REQUIRES_3DS;
        }
        if ("chargeable".equals(jsonResponse.getStatus())) {
            return AuthoriseStatus.AUTHORISED;
        }
        return AuthoriseStatus.ERROR;
    }

    @Override
    public Optional<? extends Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
        return Optional.of(new Stripe3dsRequiredParams(jsonResponse.getRedirectUrl()));
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
