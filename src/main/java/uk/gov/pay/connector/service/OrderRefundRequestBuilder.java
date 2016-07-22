package uk.gov.pay.connector.service;


import uk.gov.pay.connector.util.templates.TemplateStringBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static org.apache.commons.lang3.StringUtils.defaultString;

public class OrderRefundRequestBuilder {

    private final TemplateStringBuilder templateStringBuilder;
    private String merchantCode;
    private String transactionId;
    private String amount;

    public static OrderRefundRequestBuilder aWorldpayOrderRefundRequest() {
        return new OrderRefundRequestBuilder("/worldpay/WorldpayOrderRefundTemplate.xml");
    }

    public static OrderRefundRequestBuilder aSmartpayOrderRefundRequest() {
        return new OrderRefundRequestBuilder("/smartpay/SmartpayOrderRefundTemplate.xml");
    }

    public OrderRefundRequestBuilder(String templatePath) {
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

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("amount", amount);
        return templateStringBuilder.buildWith(templateData);
    }
}
