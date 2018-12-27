package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

import static java.lang.String.format;

public class StripeCaptureResponse implements BaseCaptureResponse {

    private final String transactionId;
    private final String errorCode;
    private final String errorMessage;

    public StripeCaptureResponse(String transactionId, String errorCode, String errorMessage) {
        this.transactionId = transactionId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "Stripe capture response (", ")");
        if (StringUtils.isNotBlank(transactionId)) {
            joiner.add("Charge gateway transaction id: " + transactionId);
        }
        if (StringUtils.isNotBlank(errorCode)) {
            joiner.add("error code: " + errorCode);
        }
        if (StringUtils.isNotBlank(errorMessage)) {
            joiner.add("error: " + errorMessage);
        }
        return joiner.toString();
    }

    public static StripeCaptureResponse from(String jsonString) {
        try {
            final String transactionId = new ObjectMapper().readValue(jsonString, Map.class).get("id").toString();
            return new StripeCaptureResponse(transactionId, null, null);
        } catch (IOException e) {
            throw new WebApplicationException(format("There was an exception parsing the payload [%s]", jsonString));
        }
    }
}
