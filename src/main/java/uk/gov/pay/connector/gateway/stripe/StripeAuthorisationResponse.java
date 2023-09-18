package uk.gov.pay.connector.gateway.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Map.entry;

public class StripeAuthorisationResponse implements BaseAuthoriseResponse {

    public static final String STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY = "customerId";
    public static final String STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY = "paymentMethodId";

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeAuthorisationResponse.class);

    private final String transactionId;
    private final AuthoriseStatus authoriseStatus;
    private final String redirectUrl;

    private final String stringifiedResponse;
    private final String customerId;
    private final String paymentMethodId;
    private final CardExpiryDate cardExpiryDate;

    private StripeAuthorisationResponse(String transactionId,
                                        AuthoriseStatus authoriseStatus,
                                        String redirectUrl,
                                        String stringifiedResponse,
                                        String customerId,
                                        String paymentMethodId,
                                        CardExpiryDate cardExpiryDate) {
        this.customerId = customerId;
        this.paymentMethodId = paymentMethodId;
        if (Objects.isNull(authoriseStatus)) {
            throw new IllegalArgumentException("Authorise status cannot be null");
        }
        this.transactionId = transactionId;
        this.authoriseStatus = authoriseStatus;
        this.redirectUrl = redirectUrl;
        this.stringifiedResponse = stringifiedResponse;
        this.cardExpiryDate = cardExpiryDate;
    }

    public static StripeAuthorisationResponse of(StripePaymentIntentResponse stripePaymentIntent) {
        return new StripeAuthorisationResponse(
                stripePaymentIntent.getId(),
                stripePaymentIntent.getAuthoriseStatus().orElse(null),
                stripePaymentIntent.getRedirectUrl().orElse(null),
                stripePaymentIntent.getStringifiedOutcome(),
                stripePaymentIntent.getCustomerId(),
                stripePaymentIntent.getPaymentMethod().getId(),
                extractCardExpiryDate(stripePaymentIntent).orElse(null)
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
                Optional.of(new Stripe3dsRequiredParams(redirectUrl));
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
        return stringifiedResponse == null ? "" : stringifiedResponse;
    }

    @Override
    public Optional<Map<String, String>> getGatewayRecurringAuthToken() {
        return Optional.ofNullable(customerId).map(id -> Map.ofEntries(
                entry(STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, customerId),
                entry(STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, paymentMethodId)
        ));
    }

    @Override
    public Optional<CardExpiryDate> getCardExpiryDate() {
        return Optional.of(cardExpiryDate);
    }

    private static Optional<CardExpiryDate> extractCardExpiryDate(StripePaymentIntentResponse stripePaymentIntent) {
        return stripePaymentIntent.getPaymentMethod().getCard().map(card -> {
            try {
                if (card.getCardExpiryYear() == null || card.getCardExpiryMonth() == null) {
                    LOGGER.info("Missing card expiry date on payment method");
                    return null;
                }
                YearMonth yearMonth = YearMonth.of(card.getCardExpiryYear(), card.getCardExpiryMonth());
                return CardExpiryDate.valueOf(yearMonth);
            } catch (DateTimeException e) {
                LOGGER.error(String.format("Invalid card expiry date in response from Stripe: %s", e.getMessage()));
                return null;
            }
        });
    }
}
