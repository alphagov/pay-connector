package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.model.StripeChargeStatus;
import uk.gov.pay.connector.gateway.stripe.util.PaymentIntentStringifier;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntent {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripePaymentIntent.class);
    
    private static Map<StripeChargeStatus, BaseAuthoriseResponse.AuthoriseStatus> statusMap = Map.of(
            StripeChargeStatus.REQUIRES_CAPTURE, BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED,
            StripeChargeStatus.REQUIRES_ACTION, BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS
    );
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("next_action")
    private NextAction nextAction;
    
    @JsonProperty("charges")
    private ChargesCollection chargesCollection;
    
    @JsonProperty("amount_capturable")
    private Long amountCapturable;

    @JsonProperty("status")
    private String status;

    @JsonProperty("last_payment_error")
    private LastPaymentError lastPaymentError;

    @JsonProperty("customer")
    private String customerId;

    @JsonProperty("payment_method")
    @JsonDeserialize(using = StripeExpandableFieldDeserializer.class)
    private StripeExpandableField<StripePaymentMethod> paymentMethod;

    public ChargesCollection getChargesCollection() {
        return chargesCollection;
    }

    public String getId() {
        return id;
    }

    public Long getAmountCapturable() {
        return amountCapturable;
    }
    
    public Optional<StripeCharge> getCharge() {
        return chargesCollection != null ? chargesCollection.getCharges().stream().findFirst() :
            Optional.empty();
    }

    public String getStatus() {
        return status;
    }

    public String getCustomerId() {
        return customerId;
    }

    public StripeExpandableField<StripePaymentMethod> getPaymentMethod() {
        return paymentMethod;
    }

    public Optional<String> getRedirectUrl() {
        return Optional.ofNullable(nextAction)
                .map(NextAction::getRedirectToUrl)
                .map(m -> m.get("url"));

    }

    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return Optional.ofNullable(statusMap.get(StripeChargeStatus.fromString(status)));

    }

    public Optional<LastPaymentError> getLastPaymentError() {
        return Optional.ofNullable(lastPaymentError);
    }

    public NextAction getNextAction() {
        return nextAction;
    }
    
    public Optional<CardExpiryDate> getCardExpiryDate() {
        if (paymentMethod == null) {
            LOGGER.error("Attempted to get card expiry date for payment intent with no payment method");
            return Optional.empty();
        }
        return getPaymentMethod().getExpanded()
                .flatMap(StripePaymentMethod::getCard)
                .map(card -> {
                    if (card.getCardExpiryYear() == null || card.getCardExpiryMonth() == null) {
                        LOGGER.info("Missing card expiry date on payment method");
                        return null;
                    }
                    try {
                        YearMonth yearMonth = YearMonth.of(card.getCardExpiryYear(), card.getCardExpiryMonth());
                        return CardExpiryDate.valueOf(yearMonth);
                    } catch (DateTimeException | IllegalArgumentException e) {
                        LOGGER.error(String.format("Invalid card expiry date in response from Stripe: %s", e.getMessage()));
                        return null;
                    }
                });
    }

    public String stringify() {
        return PaymentIntentStringifier.stringify(this);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChargesCollection {

        @JsonProperty("data")
        private List<StripeCharge> charges;

        public List<StripeCharge> getCharges() {
            return charges;
        }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NextAction {
        @JsonProperty("redirect_to_url")
        Map<String, String> redirectToUrl;

        public Map<String, String> getRedirectToUrl() {
            return redirectToUrl;
        }

        @Override
        public String toString() {
            return "NextAction{" +
                    "redirectToUrl=" + redirectToUrl +
                    '}';
        }
    }
}
