package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntentResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("next_action")
    private NextAction nextAction;
    
    @JsonProperty("client_secret")
    private String clientSecret;
    
    private String status;

    public String getId() {
        return id;
    }

    public String getClientSecret() {
        return clientSecret;
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
