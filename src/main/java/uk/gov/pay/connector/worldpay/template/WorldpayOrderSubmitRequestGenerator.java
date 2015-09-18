package uk.gov.pay.connector.worldpay.template;


import uk.gov.pay.connector.model.domain.Amount;
import uk.gov.pay.connector.model.domain.Card;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class WorldpayOrderSubmitRequestGenerator extends WorldpayRequestGenerator {

    private String merchantCode;
    private String transactionId;
    private String description;
    private Card card;
    private Amount amount;

    public WorldpayOrderSubmitRequestGenerator() {
        super("WorldpayOrderSubmitTemplate.xml");
    }

    public WorldpayOrderSubmitRequestGenerator withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public WorldpayOrderSubmitRequestGenerator withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public WorldpayOrderSubmitRequestGenerator withDescription(String description) {
        this.description = description;
        return this;
    }

    public WorldpayOrderSubmitRequestGenerator withCard(Card card) {
        this.card = card;
        return this;
    }

    public WorldpayOrderSubmitRequestGenerator withAmount(Amount amount) {
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

        return buildWith(templateData);
    }
}
