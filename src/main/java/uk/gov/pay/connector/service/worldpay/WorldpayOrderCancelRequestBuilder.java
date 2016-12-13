package uk.gov.pay.connector.service.worldpay;


import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.util.templates.TemplateStringBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class WorldpayOrderCancelRequestBuilder {

    private final TemplateStringBuilder templateStringBuilder;
    private String merchantCode;
    private String transactionId;
    private String orderType;

    public static WorldpayOrderCancelRequestBuilder aWorldpayOrderCancelRequest(String orderType) {
        return new WorldpayOrderCancelRequestBuilder(orderType);
    }

    private WorldpayOrderCancelRequestBuilder(String orderType) {
        this.orderType = orderType;
        this.templateStringBuilder = new TemplateStringBuilder("/worldpay/WorldpayOrderCancelTemplate.xml");
    }

    public WorldpayOrderCancelRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public WorldpayOrderCancelRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        return templateStringBuilder.buildWith(templateData);
    }

    public GatewayOrder buildOrder() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        return new GatewayOrder(orderType, templateStringBuilder.buildWith(templateData));
    }
}
