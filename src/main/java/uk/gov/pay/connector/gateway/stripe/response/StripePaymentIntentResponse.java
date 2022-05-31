package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.model.StripeChargeStatus;

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
    
    private String status;

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
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

    @Override
    public String toString() {
        return "StripePaymentIntentResponse{" +
                "id='" + id + '\'' +
                ", nextAction=" + nextAction +
                '}';
    }
}
