package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class GatewayAccountResourceDTO {

    @JsonProperty("gateway_account_id")
    private long accountId;

    @JsonProperty("payment_provider")
    private String paymentProvider;

    private GatewayAccountEntity.Type type;

    private String description;

    @JsonProperty("service_name")
    private String serviceName;

    @JsonProperty("analytics_id")
    private String analyticsId;

    @JsonProperty("corporate_credit_card_surcharge_amount")
    private long corporateCreditCardSurchargeAmount;

    @JsonProperty("corporate_debit_card_surcharge_amount")
    private long corporateDebitCardSurchargeAmount;

    @JsonProperty("_links")
    private Map<String, Map<String, URI>> links = new HashMap<>();

    public GatewayAccountResourceDTO() {
    }

    public GatewayAccountResourceDTO(long accountId,
                                     String paymentProvider,
                                     GatewayAccountEntity.Type type,
                                     String description,
                                     String serviceName,
                                     String analyticsId,
                                     long corporateCreditCardSurchargeAmount,
                                     long corporateDebitCardSurchargeAmount) {
        this.accountId = accountId;
        this.paymentProvider = paymentProvider;
        this.type = type;
        this.description = description;
        this.serviceName = serviceName;
        this.analyticsId = analyticsId;
        this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
        this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getType() {
        return type.toString();
    }

    public String getDescription() {
        return description;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getAnalyticsId() {
        return analyticsId;
    }

    public long getCorporateCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }

    public long getCorporateDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
    }

    public Map<String, Map<String, URI>> getLinks() {
        return links;
    }

    public void addLink(String key, URI uri) {
        links.put(key, ImmutableMap.of("href", uri));
    }

}
