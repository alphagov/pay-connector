package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.model.StripeChargeStatus;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentMethodResponse;
import uk.gov.pay.connector.gateway.stripe.util.PaymentIntentStringifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntent {

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
    private StripeExpandableField<StripePaymentMethodResponse> paymentMethod;

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

    public StripeExpandableField<StripePaymentMethodResponse> getPaymentMethod() {
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
