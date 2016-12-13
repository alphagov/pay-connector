package uk.gov.pay.connector.service;


import org.joda.time.DateTime;
import uk.gov.pay.connector.util.templates.TemplateStringBuilder;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static org.apache.commons.lang3.StringUtils.defaultString;

public class OrderCaptureRequestBuilder {

    private final TemplateStringBuilder templateStringBuilder;
    private String merchantCode;
    private String transactionId;
    private String amount;
    private DateTime date;
    private String orderType;

    public static OrderCaptureRequestBuilder aWorldpayOrderCaptureRequest(String orderType) {
        return new OrderCaptureRequestBuilder("/worldpay/WorldpayOrderCaptureTemplate.xml", orderType);
    }

    public static OrderCaptureRequestBuilder aSmartpayOrderCaptureRequest(String orderType) {
        return new OrderCaptureRequestBuilder("/smartpay/SmartpayOrderCaptureTemplate.xml", orderType);
    }

    public OrderCaptureRequestBuilder(String templatePath, String orderType) {
        this.orderType = orderType;
        this.templateStringBuilder = new TemplateStringBuilder(templatePath);
    }

    public OrderCaptureRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;

        return this;
    }

    public OrderCaptureRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = defaultString(transactionId);
        return this;
    }

    public OrderCaptureRequestBuilder withAmount(String amount) {
        this.amount = amount;
        return this;
    }

    public OrderCaptureRequestBuilder withDate(DateTime date) {
        this.date = date;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("captureDate", date); //not used for smartpay
        templateData.put("amount", amount);
        return templateStringBuilder.buildWith(templateData);
    }

    public GatewayOrder buildOrder() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("captureDate", date); //not used for smartpay
        templateData.put("amount", amount);
        return new GatewayOrder(orderType, templateStringBuilder.buildWith(templateData));
    }
}
