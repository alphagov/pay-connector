package uk.gov.pay.connector.gateway.adyen.response;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenCapture;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import java.util.StringJoiner;

public record AdyenCaptureResponse(
        String merchantAccount,
        String paymentPspReference,
        String pspReference,
        String status,
        Amount amount,
        String errorMessage
) implements BaseCaptureResponse {

    public static AdyenCaptureResponse from(AdyenCapture adyenCapture) {
        return new AdyenCaptureResponse(
                null,
                adyenCapture.paymentPspReference(),
                adyenCapture.pspReference(),
                adyenCapture.status(),
                adyenCapture.amount(),
                null
        );
    }

    public static AdyenCaptureResponse from(AdyenError adyenError) {
        return new AdyenCaptureResponse(
                null,
                null,
                null,
                adyenError.status(),
                null,
                adyenError.message()
        );
    }

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
        if (StringUtils.isNotBlank(errorMessage)) {
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
