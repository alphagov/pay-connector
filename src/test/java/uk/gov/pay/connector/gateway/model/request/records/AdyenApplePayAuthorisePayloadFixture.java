package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.adyen.request.json.AdyenApplePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;

public class AdyenApplePayAuthorisePayloadFixture {

    private String merchantAccount = "live";
    private String store = "storeId";
    private String reference = "reference";
    private Amount amount = new Amount("GBP", 10_00L);
    private AdyenApplePayPaymentMethod paymentMethod = new AdyenApplePayPaymentMethod("token");
    private String returnUrl = "http://frontend.test/card_details/abcdefghijklmnopqrstuvwxyz";

    public static AdyenApplePayAuthorisePayloadFixture anAdyenApplePayAuthorisePayloadFixture() {
        return new AdyenApplePayAuthorisePayloadFixture();
    }
    
    public AdyenApplePayAuthorisePayloadFixture withMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
        return this;
    }

    public AdyenApplePayAuthorisePayloadFixture withStore(String store) {
        this.store = store;
        return this;
    }

    public AdyenApplePayAuthorisePayloadFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public AdyenApplePayAuthorisePayloadFixture withAmount(Amount amount) {
        this.amount = amount;
        return this;
    }

    public AdyenApplePayAuthorisePayloadFixture withPaymentMethod(AdyenApplePayPaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        return this;
    }

    public AdyenApplePayAuthorisePayloadFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }
    
    public AdyenApplePayAuthorisePayload build() {
        return new AdyenApplePayAuthorisePayload(merchantAccount, store, reference, amount, paymentMethod, returnUrl);
    }
}
