package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCredentials implements GatewayCredentials {

    public static final String STRIPE_ACCOUNT_ID_KEY = "stripe_account_id";
    
    @JsonProperty(STRIPE_ACCOUNT_ID_KEY)
    @JsonView({Views.Api.class})
    private String stripeAccountId;

    public StripeCredentials() {
        // for Jackson
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
    }

    public Map<String, String> toMap() {
        return Map.of(STRIPE_ACCOUNT_ID_KEY, stripeAccountId);
    }

    @Override
    public boolean hasCredentials() {
        return stripeAccountId != null;
    }
    
}
