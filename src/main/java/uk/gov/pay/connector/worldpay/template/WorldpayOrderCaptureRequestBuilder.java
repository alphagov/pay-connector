package uk.gov.pay.connector.worldpay.template;


import org.joda.time.DateTime;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class WorldpayOrderCaptureRequestBuilder {

    private final WorldpayRequestBuilder requestBuilder;
    private String merchantCode;
    private String transactionId;
    private String amount;
    private DateTime date;

    public static WorldpayOrderCaptureRequestBuilder anOrderCaptureRequest() {
        return new WorldpayOrderCaptureRequestBuilder();
    }

    public WorldpayOrderCaptureRequestBuilder() {
        this.requestBuilder = new WorldpayRequestBuilder("WorldpayOrderCaptureTemplate.xml");
    }

    public WorldpayOrderCaptureRequestBuilder withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;

        return this;
    }

    public WorldpayOrderCaptureRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public WorldpayOrderCaptureRequestBuilder withAmount(String amount) {
        this.amount = amount;
        return this;
    }

    public WorldpayOrderCaptureRequestBuilder withDate(DateTime date) {
        this.date = date;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("captureDate", date);
        templateData.put("amount", amount);
        return requestBuilder.buildWith(templateData);
    }
}
