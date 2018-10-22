package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

//@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GatewayAccountResponse extends AbstractGatewayAccount{

    @JsonProperty("gateway_account_id")
    private final String gatewayAccountId;
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

    }

    public static class GatewayAccountResponseBuilder {

        private String providerAccountType;
        private String paymentProvider;
        private String serviceName;
        private String description;
        private String analyticsId;
        private String gatewayAccountId;
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
            List<Map<String, Object>> links = ImmutableList.of(ImmutableMap.of("href", href, "rel", "self", "method", "GET"));
            this.links = links;
            return this;
        }

        public GatewayAccountResponseBuilder location(URI href) {
            this.location = href;
            return this;
        }
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
}
