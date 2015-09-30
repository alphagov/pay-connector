package uk.gov.pay.connector.service.worldpay;


import uk.gov.pay.connector.util.templates.TemplateStringBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class WorldpayCancelOrderRequestBuilder {

    private final TemplateStringBuilder templateStringBuilder;
    private String merchantCode;
    private String transactionId;

    public static WorldpayCancelOrderRequestBuilder aCancelOrderRequest() {
        return new WorldpayCancelOrderRequestBuilder();
    }

    private WorldpayCancelOrderRequestBuilder() {
        this.templateStringBuilder = new TemplateStringBuilder("/worldpay/WorldpayOrderCancelTemplate.xml");
    }

    public WorldpayCancelOrderRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public WorldpayCancelOrderRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        return templateStringBuilder.buildWith(templateData);
    }
}
