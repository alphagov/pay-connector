package uk.gov.pay.connector.gateway.stripe.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;

import java.util.Optional;
import java.util.StringJoiner;

public class StripeRefundResponse implements BaseRefundResponse {
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

    public static StripeRefundResponse of(String reference, String errorCode, String errorMessage) {
        return new StripeRefundResponse(reference, errorCode, errorMessage);
    }

    public static StripeRefundResponse of(String reference) {
        return new StripeRefundResponse(reference);
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
