package uk.gov.pay.connector.gateway.stripe.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeAuthoriseHandler;

import java.util.Optional;
import java.util.StringJoiner;

import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.ERROR;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REJECTED;

public class StripeAuthorisationFailedResponse implements BaseAuthoriseResponse {

    private static final Logger logger = LoggerFactory.getLogger(StripeAuthorisationFailedResponse.class);

    private final StripeErrorResponse errorResponse;

    private StripeAuthorisationFailedResponse(StripeErrorResponse errorResponse) {
        this.errorResponse = errorResponse;
    }

    public static StripeAuthorisationFailedResponse of(StripeErrorResponse stripeErrorResponse) {
        return new StripeAuthorisationFailedResponse(stripeErrorResponse);
    }

    @Override
    public String getTransactionId() {
        if (errorResponse != null && errorResponse.getError() != null
                && errorResponse.getError().getStripePaymentIntent() != null) {
            return errorResponse.getError().getStripePaymentIntent().getId();
        } else {
            logger.info("Stripe error response does not contain a payment intent. It is likely that the authorisation failed when creating the payment_method.");
            return null;
        }
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        /* only `card_error` type is to be treated as REJECTED. Rest of the error types are unexpected and categorised as ERROR
           (https://stripe.com/docs/api/errors#errors-card_error)
         */
        if (errorResponse != null && errorResponse.getError() != null
                && "card_error".equals(errorResponse.getError().getType())) {
            return REJECTED;
        }
        return ERROR;
    }

    @Override
    public Optional<? extends Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
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

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Stripe authorisation failed response (", ")");
        if (errorResponse != null && errorResponse.getError() != null) {
            joiner.add(errorResponse.getError().toString());
        }

        return joiner.toString();
    }
}
