package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Map.entry;

public class StripeAuthorisationResponse implements BaseAuthoriseResponse {

    public static final String STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY = "customerId";
    public static final String STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY = "paymentMethodId";

    private final String transactionId;
    private final AuthoriseStatus authoriseStatus;
    private final String redirectUrl;

    private final String stringifiedResponse;
    private final String customerId;
    private final String paymentMethodId;

    private StripeAuthorisationResponse(String transactionId,
                                        AuthoriseStatus authoriseStatus,
                                        String redirectUrl,
                                        String stringifiedResponse,
                                        String customerId,
                                        String paymentMethodId) {
        this.customerId = customerId;
        this.paymentMethodId = paymentMethodId;
        if (Objects.isNull(authoriseStatus)) {
            throw new IllegalArgumentException("Authorise status cannot be null");
        }
        this.transactionId = transactionId;
        this.authoriseStatus = authoriseStatus;
        this.redirectUrl = redirectUrl;
        this.stringifiedResponse = stringifiedResponse;
    }

    public static StripeAuthorisationResponse of(StripePaymentIntentResponse stripePaymentIntent) {
        return new StripeAuthorisationResponse(
                stripePaymentIntent.getId(),
                stripePaymentIntent.getAuthoriseStatus().orElse(null),
                stripePaymentIntent.getRedirectUrl().orElse(null),
                stripePaymentIntent.getStringifiedOutcome(),
                stripePaymentIntent.getCustomerId(),
                stripePaymentIntent.getPaymentMethodId()
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
}
