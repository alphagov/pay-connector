package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record CreateGatewayAccountResponse (
    @JsonProperty("gateway_account_id")
    @Schema(example = "2")
    String gatewayAccountId,

    @JsonProperty("external_id")
    @Schema(example = "ab2c296ed98647e9a25f045f5e6e87a2")
    String externalId,
    
    @JsonProperty("type")
    @Schema(example = "live")
    String providerAccountType,

    @JsonProperty("service_name")
    @Schema(example = "service name")
    String serviceName,

    @JsonProperty("description")
    @Schema(example = "account for some gov org")
    String description,

    @JsonProperty("analytics_id")
    @Schema(example = "ananytics-id")
    String analyticsId,

    @JsonProperty("links")
    @Schema(example = "[" +
            "        {" +
            "            \"href\": \"https://connector.url/v1/api/accounts/2\"," +
            "            \"rel\": \"self\"," +
            "            \"method\": \"GET\"" +
            "        }" +
            "    ]")
    List<Map<String, Object>> links,

    @JsonIgnore
    URI location,

    @JsonProperty("requires_3ds")
    @Schema(example = "true")
    boolean requires3ds
) {    

    public CreateGatewayAccountResponse(GatewayAccountResponseBuilder gatewayAccountResponseBuilder) {
        this(
            gatewayAccountResponseBuilder.gatewayAccountId,
            gatewayAccountResponseBuilder.externalId,
            gatewayAccountResponseBuilder.providerAccountType,
            gatewayAccountResponseBuilder.serviceName,
            gatewayAccountResponseBuilder.description,
            gatewayAccountResponseBuilder.analyticsId,
            gatewayAccountResponseBuilder.links,
            gatewayAccountResponseBuilder.location,
            gatewayAccountResponseBuilder.requires3ds
        );
    }

    public static class GatewayAccountResponseBuilder {

        private String providerAccountType;
        private String serviceName;
        private String description;
        private String analyticsId;
        private String gatewayAccountId;
        private String externalId;
        private boolean requires3ds;
        private URI location;
        private List<Map<String, Object>> links;

        public CreateGatewayAccountResponse build() {
            return new CreateGatewayAccountResponse(this);
        }

        public GatewayAccountResponseBuilder gatewayAccountId(String gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public GatewayAccountResponseBuilder externalId(String externalId) {
            this.externalId = externalId;
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
            this.links = List.of(Map.of("href", href, "rel", "self", "method", "GET"));
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
