package uk.gov.pay.connector.gateway.adyen.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.RefundResponseBody;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;

import java.util.Optional;
import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdyenRefundResponse(
        String pspReference,
        String status,
        String errorCode,
        String errorType,
        String errorMessage
) implements BaseRefundResponse {

    public static AdyenRefundResponse from(RefundResponseBody adyenRefund) {
        return new AdyenRefundResponse(adyenRefund.pspReference(),
                adyenRefund.status(),
                null,
                null,
                null
        );
    }

    public static AdyenRefundResponse from(AdyenError adyenError) {
        return new AdyenRefundResponse(null,
                null,
                adyenError.errorCode(),
                adyenError.errorType(),
                adyenError.message()
        );
    }

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(pspReference);
    }

    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "Adyen refund response(", ")");

        if (isNotBlank(pspReference)) {
            joiner.add("pspReference: " + pspReference);
        }
        if (isNotBlank(status)) {
            joiner.add("status: " + status);
        }
        if (isNotBlank(errorCode)) {
            joiner.add("errorCode: " + errorCode);
        }
        if (isNotBlank(errorType)) {
            joiner.add("errorType: " + errorType);
        }
        if (isNotBlank(errorMessage)) {
            joiner.add("errorMessage: " + errorMessage);
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
