package uk.gov.pay.connector.gateway.stripe.response;

import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;

public class StripeRefundResponse implements BaseRefundResponse {
    private String reference;
    private String errorCode;
    private String errorMessage;

    private StripeRefundResponse(String reference) {
        this.reference = reference;
    }

    public static StripeRefundResponse of(Response response) {
        final String id = response.readEntity(Map.class).get("id").toString();
        return new StripeRefundResponse(id);
    }

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}
