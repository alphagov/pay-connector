package uk.gov.pay.connector.worldpay.template;


import org.joda.time.DateTime;
import uk.gov.pay.connector.model.domain.Amount;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class WorldpayOrderCaptureRequestGenerator extends WorldpayRequestGenerator {

    private String merchantCode;
    private String transactionId;
    private Amount amount;
    private DateTime date;

    public WorldpayOrderCaptureRequestGenerator() {
        super("WorldpayOrderCaptureTemplate.xml");
    }

    public WorldpayOrderCaptureRequestGenerator withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;

        return this;
    }

    public WorldpayOrderCaptureRequestGenerator withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public WorldpayOrderCaptureRequestGenerator withAmount(Amount amount) {
        this.amount = amount;
        return this;
    }

    public WorldpayOrderCaptureRequestGenerator withDate(DateTime date) {
        this.date = date;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("captureDate", date);
        templateData.put("amount", amount);
        return buildWith(templateData);
    }
}
