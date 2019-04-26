package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.inject.Inject;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCharge {
    @Inject
    ObjectMapper mapper;
    
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
