package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import javax.ws.rs.core.Response;
import java.util.Optional;

import static java.lang.String.format;

public class StripeAuthorisationResponse implements BaseAuthoriseResponse {

    private StripeJsonResponse jsonResponse;

    private StripeAuthorisationResponse(StripeJsonResponse jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    @Override
    public String getTransactionId() {
        return jsonResponse.getTransactionId();
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        String stripeStatus = jsonResponse.getStatus();
        switch (stripeStatus) {
            case "succeeded": return AuthoriseStatus.AUTHORISED;
            default: throw new IllegalArgumentException(format("Cannot map stripe status of %s to an %s", stripeStatus, AuthoriseStatus.class.getName()));
        }
    }

    @Override
    public Optional<? extends GatewayParamsFor3ds> getGatewayParamsFor3ds() {
        return Optional.empty();
    }

    @Override
    public String getErrorCode() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    public static StripeAuthorisationResponse of(Response response) {
        return new StripeAuthorisationResponse(response.readEntity(StripeJsonResponse.class));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class StripeJsonResponse {
        @JsonProperty("id")
        private String id;
        @JsonProperty("status")
        private String status;

        public String getTransactionId() {
            return id;
        }

        public String getStatus() {
            return status;
        }
    }
}
