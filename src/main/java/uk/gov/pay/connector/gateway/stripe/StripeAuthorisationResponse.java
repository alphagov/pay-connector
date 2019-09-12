package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.response.StripeParamsFor3ds;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;

import java.util.Objects;
import java.util.Optional;

public class StripeAuthorisationResponse implements BaseAuthoriseResponse {

    private final String transactionId;
    private final AuthoriseStatus authoriseStatus;
    private final String redirectUrl;

    private StripeAuthorisationResponse(String transactionId, AuthoriseStatus authoriseStatus, String redirectUrl) {
        if (Objects.isNull(authoriseStatus )) {
            throw new IllegalArgumentException("Authorise status cannot be null");
        }
        this.transactionId = transactionId;
        this.authoriseStatus = authoriseStatus;
        this.redirectUrl = redirectUrl;
    }

    public static StripeAuthorisationResponse of(StripePaymentIntentResponse stripePaymentIntent) {
        return new StripeAuthorisationResponse(
                stripePaymentIntent.getId(),
                stripePaymentIntent.getAuthoriseStatus().orElse(null),
                stripePaymentIntent.getRedirectUrl().orElse(null)
        );
    }

    public static StripeAuthorisationResponse of(StripeCharge stripeCharge) {
        return new StripeAuthorisationResponse(
                stripeCharge.getId(),
                stripeCharge.getAuthorisationStatus().orElse(null),
                null
        );
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        return authoriseStatus;
    }

    @Override
    public Optional<GatewayParamsFor3ds> getGatewayParamsFor3ds() {
        return Optional.ofNullable(redirectUrl)
                .map(StripeParamsFor3ds::new);
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
