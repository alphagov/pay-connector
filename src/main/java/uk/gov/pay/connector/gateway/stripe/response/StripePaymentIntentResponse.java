package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeExpandableField;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripeExpandableFieldDeserializer;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.model.StripeChargeStatus;
import uk.gov.pay.connector.gateway.stripe.util.PaymentIntentStringifier;

import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntentResponse {
    private static Map<StripeChargeStatus, BaseAuthoriseResponse.AuthoriseStatus> statusMap = Map.of(
            StripeChargeStatus.REQUIRES_CAPTURE, BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED,
            StripeChargeStatus.REQUIRES_ACTION, BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS
            
    );
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("next_action")
    private NextAction nextAction;

    @JsonProperty("charges")
    private StripePaymentIntent.ChargesCollection chargesCollection;

    private String status;
    
    @JsonProperty("customer")
    private String customerId;
    
    @JsonProperty("payment_method")
    private StripeExpandableField<StripePaymentMethodResponse> paymentMethod;

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getCustomerId() {
        return customerId;
    }

    @JsonDeserialize(using = StripeExpandableFieldDeserializer.class)
    public StripeExpandableField<StripePaymentMethodResponse> getPaymentMethod() {
        return paymentMethod;
    }

    public Optional<String> getRedirectUrl() {
        return Optional.ofNullable(nextAction)
                .map(NextAction::getRedirectToUrl)
                .map(m -> m.get("url"));
                
    }

    public NextAction getNextAction() {
        return nextAction;
    }
    
    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return Optional.ofNullable(statusMap.get(StripeChargeStatus.fromString(status)));

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

    public Optional<StripeCharge> getCharge() {
        return chargesCollection != null ? chargesCollection.getCharges().stream().findFirst() :
                Optional.empty();
    }

    @Override
    public String toString() {
        return "StripePaymentIntentResponse{" +
                "id='" + id + '\'' +
                ", nextAction=" + nextAction +
                '}';
    }

    public String getStringifiedOutcome() {
        return PaymentIntentStringifier.stringify(this);
    }
}
