package uk.gov.pay.connector.worldpay.template;


import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.Session;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class WorldpayOrderSubmitRequestGenerator extends WorldpayRequestGenerator {

    private String merchantCode;
    private String transactionId;
    private Session session;
    private String description;
    private Card card;
    private Amount amount;
    private Browser browser;

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

    public WorldpayOrderSubmitRequestGenerator withSession(Session session) {
        this.session = session;
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

    public WorldpayOrderSubmitRequestGenerator withBrowser(Browser browser) {
        this.browser = browser;
        return this;
    }

    public String build() {
        Map<String, Object> templateData = newHashMap();
        templateData.put("merchantCode", merchantCode);
        templateData.put("transactionId", transactionId);
        templateData.put("description", description);
        templateData.put("amount", amount);
        templateData.put("card", card);
        templateData.put("session", session);
        templateData.put("browser", browser);

        return buildWith(templateData);
    }
}
