package uk.gov.pay.connector.gateway.stripe.response;

import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import javax.ws.rs.core.Response;
import java.util.Map;

public class StripeCaptureResponse implements BaseCaptureResponse {

    private String transactionId;

    private StripeCaptureResponse(String id) {
        this.transactionId = id;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
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
        return new StripeCaptureResponse((String) response.readEntity(Map.class).get("id"));
    }

}
