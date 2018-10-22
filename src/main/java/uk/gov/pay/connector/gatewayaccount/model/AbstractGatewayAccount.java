package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class AbstractGatewayAccount {

    @JsonProperty("type")
    protected String providerAccountType;
    @JsonProperty("service_name")
    protected String serviceName;
    @JsonProperty("description")
    protected String description;
    @JsonProperty("analytics_id")
    protected String analyticsId;

    public String getProviderAccountType() {
        return providerAccountType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getDescription() {
        return description;
    }

    public String getAnalyticsId() {
        return analyticsId;
    }

}
