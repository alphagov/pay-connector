package uk.gov.pay.connector.service.smartpay;


import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.util.templates.TemplateStringBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class SmartpayOrderCancelRequestBuilder {

    private final TemplateStringBuilder templateStringBuilder;
    private String merchantCode;
    private String transactionId;
    private String orderType;

    public static SmartpayOrderCancelRequestBuilder aSmartpayOrderCancelRequest(String orderType) {
        return new SmartpayOrderCancelRequestBuilder(orderType);
    }

    private SmartpayOrderCancelRequestBuilder(String orderType) {
        this.orderType = orderType;
        this.templateStringBuilder = new TemplateStringBuilder("/smartpay/SmartpayOrderCancelTemplate.xml");
    }

    public SmartpayOrderCancelRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public SmartpayOrderCancelRequestBuilder withTransactionId(String transactionId) {
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
