package uk.gov.pay.connector.gateway.stripe.json;

import com.stripe.model.PaymentMethod;
import com.stripe.model.StripeError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason;
import uk.gov.pay.connector.gateway.model.StripeAuthorisationRejectedCodeMapper;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.YearMonth;
import java.util.Optional;
import java.util.StringJoiner;

import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.ERROR;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REJECTED;

public class StripeAuthorisationFailedResponse implements BaseAuthoriseResponse {

    private static final Logger logger = LoggerFactory.getLogger(StripeAuthorisationFailedResponse.class);

    private final StripeError stripeError;

    private StripeAuthorisationFailedResponse(StripeError stripeError) {
        this.stripeError = stripeError;
    }

    public static StripeAuthorisationFailedResponse of(StripeError stripeErrorResponse) {
        return new StripeAuthorisationFailedResponse(stripeErrorResponse);
    }

    @Override
    public String getTransactionId() {
        if (stripeError != null && stripeError.getPaymentIntent() != null) {
            return stripeError.getPaymentIntent().getId();
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
        if (stripeError != null && ("card_error".equals(stripeError.getType())
                || ("invalid_request_error".equals(stripeError.getType())
                && "card_decline_rate_limit_exceeded".equals(stripeError.getCode())))) {
            return REJECTED;
        }
        return ERROR;
    }

    @Override
    public Optional<MappedAuthorisationRejectedReason> getMappedAuthorisationRejectedReason() {
        if (authoriseStatus() != AuthoriseStatus.REJECTED) {
            return Optional.empty();
        }

        var mappedAuthorisationRejectedReason = Optional.ofNullable(stripeError)
                .flatMap(x -> Optional.ofNullable(stripeError.getPaymentIntent()))
                .flatMap(y -> Optional.ofNullable(y.getLastPaymentError()))
                .map(StripeError::getDeclineCode)
                .map(StripeAuthorisationRejectedCodeMapper::toMappedAuthorisationRejectionReason)
                .orElse(MappedAuthorisationRejectedReason.UNCATEGORISED);

        return Optional.of(mappedAuthorisationRejectedReason);
    }

    @Override
    public Optional<? extends Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
        return Optional.empty();
    }

    @Override
    public Optional<CardExpiryDate> getCardExpiryDate() {
        return Optional.ofNullable(stripeError)
                .flatMap(x -> Optional.ofNullable(stripeError.getPaymentIntent()))
                .flatMap(x -> {
                    PaymentMethod.Card card = x.getPaymentMethodObject().getCard();
                    return Optional.of(CardExpiryDate.valueOf(YearMonth.of(card.getExpYear().intValue(), card.getExpMonth().intValue())));
                });
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
        if (stripeError != null) {
            joiner.add(stripeError.toString());
        }

        return joiner.toString();
    }
}
