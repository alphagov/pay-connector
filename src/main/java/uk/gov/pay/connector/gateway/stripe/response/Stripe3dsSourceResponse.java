package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Stripe3dsSourceResponse {

    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private String status;
    @JsonProperty("redirect")
    private Redirect redirect;

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getRedirectUrl() {
        return redirect.getUrl();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Redirect {
        @JsonProperty("url")
        private String url;

        public String getUrl() {
            return url;
        }
    }
}
