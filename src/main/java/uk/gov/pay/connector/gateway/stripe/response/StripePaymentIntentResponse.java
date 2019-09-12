package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse.ProcessableStatus.REQUIRES_ACTION;
import static uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse.ProcessableStatus.REQUIRES_CAPTURE;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripePaymentIntentResponse {
    
    private static Map<String, BaseAuthoriseResponse.AuthoriseStatus> statusMap = Map.of(
            REQUIRES_CAPTURE.getStatus(), BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED,
            REQUIRES_ACTION.getStatus(), BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS
            
    );
    
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
    

    public Optional<ProcessableStatus> getProcessableStatus() {
        return ProcessableStatus.fromString(status);
    } 

    public NextAction getNextAction() {
        return nextAction;
    }
    
    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return Optional.ofNullable(statusMap.get(status));

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
    
    public enum ProcessableStatus {
        REQUIRES_ACTION("requires_action"),
        REQUIRES_CONFIRMATION("requires_confirmation"),
        REQUIRES_CAPTURE("requires_capture");
        
        private final String status;
        ProcessableStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        static Optional<ProcessableStatus> fromString(String name) {
            return Arrays.stream(ProcessableStatus.values())
                    .filter(v -> v.status.equals(name))
                    .findFirst();
        }
    }
}
