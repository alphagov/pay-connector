package uk.gov.pay.connector.gateway.stripe;

import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.model.StripeChargeStatus;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gateway.stripe.util.PaymentIntentStringifier;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Map.entry;

public class StripeAuthorisationResponse implements BaseAuthoriseResponse {

    public static final String STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY = "customerId";
    public static final String STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY = "paymentMethodId";

    private static final Map<StripeChargeStatus, BaseAuthoriseResponse.AuthoriseStatus> STATUS_MAP = Map.of(
            StripeChargeStatus.REQUIRES_CAPTURE, BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED,
            StripeChargeStatus.REQUIRES_ACTION, BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS);

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

    public static StripeAuthorisationResponse of(PaymentIntent paymentIntent) {
        PaymentMethod.Card card = paymentIntent.getPaymentMethodObject().getCard();
        String redirectUrl = Optional.ofNullable(paymentIntent.getNextAction()).map(PaymentIntent.NextAction::getRedirectToUrl)
                .map(PaymentIntent.NextActionRedirectToUrl::getUrl).orElse(null);

        return new StripeAuthorisationResponse(
                paymentIntent.getId(),
                STATUS_MAP.get(StripeChargeStatus.fromString(paymentIntent.getStatus())),
                redirectUrl,
                PaymentIntentStringifier.stringify(paymentIntent),
                paymentIntent.getCustomer(),
                paymentIntent.getPaymentMethod(),
                CardExpiryDate.valueOf(YearMonth.of(card.getExpYear().intValue(), card.getExpMonth().intValue()))
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
}
