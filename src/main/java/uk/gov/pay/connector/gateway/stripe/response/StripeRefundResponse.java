package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;

import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static java.lang.String.format;

public class StripeRefundResponse implements BaseRefundResponse {
    private static final Logger logger = LoggerFactory.getLogger(StripeRefundResponse.class);
    private String reference;
    private String errorCode;
    private String errorMessage;

    private StripeRefundResponse(String reference) {
        this.reference = reference;
    }

    private StripeRefundResponse(String reference, String errorCode, String errorMessage) {
        this.reference = reference;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static StripeRefundResponse of(String errorCode, String errorMessage) {
        return new StripeRefundResponse(null, errorCode, errorMessage);
    }

    public static StripeRefundResponse of(String reference) {
        return new StripeRefundResponse(reference);
    }

    public static StripeRefundResponse fromJsonString(String jsonString) {
        try {
            String reference = new ObjectMapper().readValue(jsonString, Map.class).get("id").toString();
            return of(reference);
        } catch (IOException e) {
            logger.error("There was an error parsing the payload [{}]", jsonString);
            throw new WebApplicationException(format("Payload cannot be parsed [%s]", jsonString));
        }
    }

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public String stringify() {
        return toString();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Stripe refund response (", ")");
        if (StringUtils.isNotBlank(reference)) {
            joiner.add("Refund gateway reference id: " + reference);
        }
        if (StringUtils.isNotBlank(errorCode)) {
            joiner.add("error code: " + errorCode);
        }
        if (StringUtils.isNotBlank(errorMessage)) {
            joiner.add("error: " + errorMessage);
        }
        return joiner.toString();
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
