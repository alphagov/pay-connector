package uk.gov.pay.connector.gateway.adyen.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.CancelResponseBody;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus.ERROR;
import static uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus.SUBMITTED;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdyenCancelResponse(
        String pspReference,
        String status,
        String errorCode,
        String errorType,
        String errorMessage
) implements BaseCancelResponse {

    public static AdyenCancelResponse from(CancelResponseBody adyenCancelResponse) {
        return new AdyenCancelResponse(adyenCancelResponse.paymentPspReference(),
                adyenCancelResponse.status(),
                null,
                null,
                null
        );
    }

    public static AdyenCancelResponse from(AdyenError adyenError) {
        return new AdyenCancelResponse(null,
                null,
                adyenError.errorCode(),
                adyenError.errorType(),
                adyenError.message()
        );
    }
    
    public static BaseCancelResponse from(GatewayError gatewayError) {
        return new BaseCancelResponse() {
            @Override
            public String getTransactionId() {
                return "";
            }

            @Override
            public CancelStatus cancelStatus() {
                return null;
            }

            @Override
            public String getErrorCode() {
                return "";
            }

            @Override
            public String getErrorMessage() {
                return "";
            }
        };
    }
    
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

    @Override
    public String getTransactionId() {
        return pspReference;
    }

    @Override
    public CancelStatus cancelStatus() {
        if ("received".equals(status)) {
            return SUBMITTED;
        }
        return ERROR;
    }
}
