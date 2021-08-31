package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
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
    public Optional<Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
        return redirectUrl == null ? Optional.empty() :
                Optional.of(new Stripe3dsRequiredParams(redirectUrl, null));
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
