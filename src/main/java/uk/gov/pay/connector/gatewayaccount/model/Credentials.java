package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "credentials_type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StripeCredentials.class, name = "stripe"),
})
public abstract class Credentials {
    
    public abstract Map<String, String> toMap();
    
}
