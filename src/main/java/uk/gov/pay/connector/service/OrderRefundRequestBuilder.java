package uk.gov.pay.connector.service;


import uk.gov.pay.connector.util.templates.TemplateStringBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static org.apache.commons.lang3.StringUtils.defaultString;

public class OrderRefundRequestBuilder {

    private final TemplateStringBuilder templateStringBuilder;
    private String merchantCode;
    private String transactionId;
    private String reference;
    private String amount;
    private String orderType;

    public static OrderRefundRequestBuilder aWorldpayOrderRefundRequest(String orderType) {
        return new OrderRefundRequestBuilder("/worldpay/WorldpayOrderRefundTemplate.xml", orderType);
    }

    public static OrderRefundRequestBuilder aSmartpayOrderRefundRequest(String orderType) {
        return new OrderRefundRequestBuilder("/smartpay/SmartpayOrderRefundTemplate.xml", orderType);
    }

    public OrderRefundRequestBuilder(String templatePath, String orderType) {
        this.orderType = orderType;
        this.templateStringBuilder = new TemplateStringBuilder(templatePath);
    }

    public OrderRefundRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public OrderRefundRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = defaultString(transactionId);
        return this;
    }

    public OrderRefundRequestBuilder withAmount(String amount) {
        this.amount = amount;
        return this;
    }

    public OrderRefundRequestBuilder withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("amount", amount);
        templateData.put("reference", reference);
        return templateStringBuilder.buildWith(templateData);
    }

    public GatewayOrder buildOrder() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("amount", amount);
        templateData.put("reference", reference);
        return new GatewayOrder(orderType, templateStringBuilder.buildWith(templateData));
    }
}
