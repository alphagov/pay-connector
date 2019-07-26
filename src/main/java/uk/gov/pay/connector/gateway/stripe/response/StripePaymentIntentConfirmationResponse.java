package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntentConfirmationResponse {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("next_action")
    private NextAction nextAction;


    public String getId() {
        return id;
    }

    public String getRedirectUrl() {
        return nextAction.getRedirectToUrl().get("url");
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
        return "StripePaymentIntentConfirmationResponse{" +
                "id='" + id + '\'' +
                ", nextAction=" + nextAction +
                '}';
    }
}
