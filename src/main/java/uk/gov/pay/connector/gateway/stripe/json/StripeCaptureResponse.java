package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.CaptureResponse;


@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCaptureResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("balance_transaction")
    private BalanceTransaction balanceTransaction;

    public String getId() {
        return id;
    }

    public BalanceTransaction getBalanceTransaction() {
        return balanceTransaction;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BalanceTransaction {
        @JsonProperty("fee")
        private Long feeAmount;

        public Long getAmount() {
            return feeAmount;
        }
    }

    public CaptureResponse toCaptureResponse() {
        return new CaptureResponse(getId(), CaptureResponse.ChargeState.PENDING, null, toString(), balanceTransaction.getAmount());
    }
}
