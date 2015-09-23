package uk.gov.pay.connector.worldpay.template;


import uk.gov.pay.connector.model.domain.Card;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class WorldpayOrderSubmitRequestBuilder {

    private final WorldpayRequestBuilder requestBuilder;
    private String merchantCode;
    private String transactionId;
    private String description;
    private Card card;
    private String amount;

    public static WorldpayOrderSubmitRequestBuilder anOrderSubmitRequest() {
        return new WorldpayOrderSubmitRequestBuilder();
    }

    public WorldpayOrderSubmitRequestBuilder() {
        requestBuilder = new WorldpayRequestBuilder("WorldpayOrderSubmitTemplate.xml");
    }

    public WorldpayOrderSubmitRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public WorldpayOrderSubmitRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public WorldpayOrderSubmitRequestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public WorldpayOrderSubmitRequestBuilder withCard(Card card) {
        this.card = card;
        return this;
    }

    public WorldpayOrderSubmitRequestBuilder withAmount(String amount) {
        this.amount = amount;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("description", description);
        templateData.put("amount", amount);
        templateData.put("card", card);

        return requestBuilder.buildWith(templateData);
    }
}
