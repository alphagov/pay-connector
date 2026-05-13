package uk.gov.pay.connector.gateway.adyen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.adyen.model.json.Amount;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import java.util.StringJoiner;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdyenCaptureResponse(
        @JsonProperty("merchantAccount")
        String merchantAccount,
        @JsonProperty("paymentPspReference")
        String paymentPspReference,
        @JsonProperty("pspReference")
        String pspReference,
        @JsonProperty("status")
        String status,
        @JsonProperty("amount")
        Amount amount,
        @JsonProperty("message")
        String errorMessage
) implements BaseCaptureResponse {
    /**
     * PSP reference provided when payment is authorised
     */
    @Override
    public String getTransactionId() {
        return paymentPspReference;
    }

    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "Adyen capture response(", ")");
        if (StringUtils.isNotBlank(merchantAccount)) {
            joiner.add("merchantAccount: " + merchantAccount);
        }
        if (StringUtils.isNotBlank(paymentPspReference)) {
            joiner.add("paymentPspReference: " + paymentPspReference);
        }
        if (StringUtils.isNotBlank(pspReference)) {
            joiner.add("pspReference: " + pspReference);
        }
        if (StringUtils.isNotBlank(status)) {
            joiner.add("status: " + status);
        }
        if (StringUtils.isNotBlank(errorMessage )) {
            joiner.add("errorMessage: " + errorMessage);
        }
        return joiner.toString();
    }

    @Override
    public String getErrorCode() {
        return !status.equals("received") ? status : null;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}
