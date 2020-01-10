package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class GatewayAccountResponse {

    @JsonProperty("type")
    private final String providerAccountType;

    @JsonProperty("service_name")
    private final String serviceName;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("analytics_id")
    private final String analyticsId;

    @JsonProperty("gateway_account_id")
    private final String gatewayAccountId;

    @JsonProperty("requires_3ds")
    private final boolean requires3ds;

    @JsonProperty("links")
    private final List<Map<String, Object>> links;

    @JsonIgnore
    private final URI location;

    public GatewayAccountResponse(GatewayAccountResponseBuilder gatewayAccountResponseBuilder) {
        this.gatewayAccountId = gatewayAccountResponseBuilder.gatewayAccountId;
        this.providerAccountType = gatewayAccountResponseBuilder.providerAccountType;
        this.serviceName = gatewayAccountResponseBuilder.serviceName;
        this.description = gatewayAccountResponseBuilder.description;
        this.analyticsId = gatewayAccountResponseBuilder.analyticsId;
        this.links = gatewayAccountResponseBuilder.links;
        this.location = gatewayAccountResponseBuilder.location;
        this.requires3ds = gatewayAccountResponseBuilder.requires3ds;
    }

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

    public String getGatewayAccountId() {
        return gatewayAccountId;
    }

    public List<Map<String, Object>> getLinks() {
        return links;
    }

    public URI getLocation() {
        return location;
    }

    public boolean getRequires3ds() {
        return requires3ds;
    }

    public static class GatewayAccountResponseBuilder {

        private String providerAccountType;
        private String paymentProvider;
        private String serviceName;
        private String description;
        private String analyticsId;
        private String gatewayAccountId;
        private boolean requires3ds;
        private URI location;
        private List<Map<String, Object>> links;

        public GatewayAccountResponse build() {
            return new GatewayAccountResponse(this);
        }

        public GatewayAccountResponseBuilder gatewayAccountId(String gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public GatewayAccountResponseBuilder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public GatewayAccountResponseBuilder providerAccountType(String providerAccountType) {
            this.providerAccountType = providerAccountType;
            return this;
        }

        public GatewayAccountResponseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public GatewayAccountResponseBuilder analyticsId(String analyticsId) {
            this.analyticsId = analyticsId;
            return this;
        }

        public GatewayAccountResponseBuilder generateLinks(URI href) {
            this.links = ImmutableList.of(ImmutableMap.of("href", href, "rel", "self", "method", "GET"));
            return this;
        }

        public GatewayAccountResponseBuilder location(URI href) {
            this.location = href;
            return this;
        }

        public GatewayAccountResponseBuilder requires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }
    }
}
