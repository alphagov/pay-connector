package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.adyen.request.json.AdyenApplePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;

public class AdyenApplePayAuthoriseRequestFixture {

    private String merchantAccount = "live";
    private String store = "storeId";
    private String reference = "reference";
    private Amount amount = new Amount("GBP", 10_00L);
    private AdyenApplePayPaymentMethod paymentMethod = new AdyenApplePayPaymentMethod("token");
    private String returnUrl = "http://frontend.test/card_details/abcdefghijklmnopqrstuvwxyz";

    public static AdyenApplePayAuthoriseRequestFixture anAdyenApplePayAuthoriseRequestFixture() {
        return new AdyenApplePayAuthoriseRequestFixture();
    }
    
    public AdyenApplePayAuthoriseRequestFixture withMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
        return this;
    }

    public AdyenApplePayAuthoriseRequestFixture withStore(String store) {
        this.store = store;
        return this;
    }

    public AdyenApplePayAuthoriseRequestFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public AdyenApplePayAuthoriseRequestFixture withAmount(Amount amount) {
        this.amount = amount;
        return this;
    }

    public AdyenApplePayAuthoriseRequestFixture withPaymentMethod(AdyenApplePayPaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        return this;
    }

    public AdyenApplePayAuthoriseRequestFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }
    
    public AdyenApplePayAuthoriseRequest build() {
        return new AdyenApplePayAuthoriseRequest(merchantAccount, store, reference, amount, paymentMethod, returnUrl);
    }
}
