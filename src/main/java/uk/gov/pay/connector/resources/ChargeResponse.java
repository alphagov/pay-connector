package uk.gov.pay.connector.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.*;


@JsonInclude(Include.NON_NULL)
public class ChargeResponse {

    @JsonProperty("links")
    private List<Map<String, Object>> dataLinks = new ArrayList<>();
    @JsonProperty("charge_id")
    private String chargeId;

    @JsonProperty
    private Long amount;

    @JsonProperty
    private String status;

    @JsonProperty("gateway_transaction_id")
    private String gatewayTransactionId;

    @JsonProperty("return_url")
    private String returnUrl;

    @JsonProperty
    private String description;

    @JsonProperty
    private String reference;

    @JsonProperty("payment_provider")
    private String providerName;

    private ZonedDateTime createdDate;

    @JsonProperty("created_date")
    public String getCreatedDate() {
        return DateTimeUtils.toUTCDateString(createdDate);
    }

    private ChargeResponse(String chargeId, Long amount, String status, String gatewayTransactionId, String returnUrl, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks) {
        this.dataLinks = dataLinks;
        this.chargeId = chargeId;
        this.amount = amount;
        this.status = status;
        this.gatewayTransactionId = gatewayTransactionId;
        this.returnUrl = returnUrl;
        this.description = description;
        this.reference = reference;
        this.providerName = providerName;
        this.createdDate = createdDate;
    }

    public static class Builder {

        private String chargeId;
        private Long amount;
        private String status;
        private String gatewayTransactionId;
        private String returnUrl;
        private String description;
        private ZonedDateTime createdDate;
        private List<Map<String, Object>> links = new ArrayList<>();
        private String reference;
        private String providerName;

        private Builder() {
        }

        public static Builder aChargeResponse() {
            return new Builder();
        }

        public Builder withChargeId(String chargeId) {
            this.chargeId = chargeId;
            return this;
        }

        public Builder withAmount(Long amount) {
            this.amount = amount;
            return this;
        }

        public Builder withStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return this;
        }

        public Builder withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withReference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder withCreatedDate(ZonedDateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Builder withLink(String rel, String method, URI href) {
            links.add(ImmutableMap.of(
                    "rel", rel,
                    "method", method,
                    "href", href
            ));
            return this;
        }

        public ChargeResponse build() {
            return new ChargeResponse(chargeId, amount, status, gatewayTransactionId, returnUrl, description, reference, providerName, createdDate, links);
        }

        public Builder withProviderName(String providerName) {
            this.providerName = providerName;
            return this;
        }
    }
}
