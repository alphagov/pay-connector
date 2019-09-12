package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCharge {
    private ObjectMapper mapper = new ObjectMapper();
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("balance_transaction")
    private Object rawBalanceTransaction;

    @JsonProperty("destination")
    private String destinationAccountId;

    @JsonProperty("status")
    private String status;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BalanceTransaction {

        @JsonProperty("fee")
        private Long fee;

        public Long getFee() {
            return fee;
        }

    }

    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthorisationStatus() {
        return Optional.ofNullable(status)
                .filter("succeeded"::equals)
                .map(s -> BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
    }

    public String getId() {
        return id;
    }

    public Optional<Long> getFee() {
        if (rawBalanceTransaction instanceof String) {
            return Optional.empty();
        }
        BalanceTransaction balanceTransaction = mapper.convertValue(rawBalanceTransaction, BalanceTransaction.class);
        
        return Optional.ofNullable(balanceTransaction.getFee());
    }

    public boolean isPlatformCharge() {
        return !StringUtils.isNotEmpty(destinationAccountId);
    }

    public String getStatus() {
        return status;
    }
}
