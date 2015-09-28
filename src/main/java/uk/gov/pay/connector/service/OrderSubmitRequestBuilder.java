package uk.gov.pay.connector.service;


import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.util.templates.TemplateStringBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class OrderSubmitRequestBuilder {

    private final TemplateStringBuilder templateStringBuilder;
    private String merchantCode;
    private String transactionId;
    private String description;
    private Card card;
    private String amount;

    public static OrderSubmitRequestBuilder aWorldpayOrderSubmitRequest() {
        return new OrderSubmitRequestBuilder("/worldpay/WorldpayOrderSubmitTemplate.xml");
    }

    public static OrderSubmitRequestBuilder aSmartpayOrderSubmitRequest() {
        return new OrderSubmitRequestBuilder("/smartpay/SmartpayOrderSubmitTemplate.xml");
    }

    public OrderSubmitRequestBuilder(String templatePath) {
        templateStringBuilder = new TemplateStringBuilder(templatePath);
    }

    public OrderSubmitRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public OrderSubmitRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public OrderSubmitRequestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public OrderSubmitRequestBuilder withCard(Card card) {
        this.card = card;
        return this;
    }

    public OrderSubmitRequestBuilder withAmount(String amount) {
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

        return templateStringBuilder.buildWith(templateData);
    }
}
