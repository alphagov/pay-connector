package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCaptureResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("balance_transaction")
    private BalanceTransaction balanceTransaction;



    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BalanceTransaction {

        @JsonProperty("fee")
        private Long fee;

        public Long getFee() {
            return fee;
        }
    }

    public String getId() {
        return id;
    }

    public Long getFee() {
        return balanceTransaction.getFee();
    }
}
