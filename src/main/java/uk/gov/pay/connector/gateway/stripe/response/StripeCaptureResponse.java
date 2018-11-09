package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import javax.ws.rs.core.Response;

public class StripeCaptureResponse implements BaseCaptureResponse {

    private StripeCaptureJsonResponse jsonResponse;

    private StripeCaptureResponse(StripeCaptureJsonResponse jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    @Override
    public String getTransactionId() {
        return jsonResponse.getTransactionId();
    }

    @Override
    public String getErrorCode() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    public static StripeCaptureResponse of(Response response) {
        return new StripeCaptureResponse(response.readEntity(StripeCaptureJsonResponse.class));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class StripeCaptureJsonResponse {
        @JsonProperty("id")
        private String id;
        @JsonProperty("captured")
        private String captured;

        public String getTransactionId() {
            return id;
        }

        public String getStatus() {
            return captured;
        }
    }

}
