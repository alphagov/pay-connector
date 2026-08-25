package uk.gov.pay.connector.gateway.model.request.records;

import com.adyen.model.payment.BrowserInfo;
import uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenGooglePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenBrowserInfo;
import uk.gov.pay.connector.model.domain.googlepay.AdyenGooglePayAuthRequestFixture;
import uk.gov.pay.connector.wallets.googlepay.api.AdyenGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayPaymentInfo;

import static uk.gov.pay.connector.model.domain.googlepay.GooglePayPaymentInfoFixture.aGooglePayPaymentInfo;

public class AdyenGooglePayAuthoriseRequestFixture {

    private AdyenGooglePayAuthRequest googlePayAuthRequest = AdyenGooglePayAuthRequestFixture.aGooglePayAuthRequest().withGooglePaymentInfo(aGooglePayPaymentInfo().build()).build();
    private String merchantAccount = "live";
    private String store = "storeId";
    private String reference = "reference";
    private Amount amount = new Amount("GBP", 10_00L);
    private AdyenGooglePayPaymentMethod paymentMethod = new AdyenGooglePayPaymentMethod("token");
    private AdyenBrowserInfo browserInfo = new AdyenBrowserInfoFactory().create(googlePayAuthRequest.getPaymentInfo());
    private String returnUrl = "http://frontend.test/card_details/abcdefghijklmnopqrstuvwxyz";

    public static AdyenGooglePayAuthoriseRequestFixture anAdyenGooglePayAuthoriseRequestFixture() {
        return new AdyenGooglePayAuthoriseRequestFixture();
    }
    
    public AdyenGooglePayAuthoriseRequestFixture withMerchantAccount(String merchantAccount) {
        this.merchantAccount = merchantAccount;
        return this;
    }

    public AdyenGooglePayAuthoriseRequestFixture withStore(String store) {
        this.store = store;
        return this;
    }

    public AdyenGooglePayAuthoriseRequestFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public AdyenGooglePayAuthoriseRequestFixture withAmount(Amount amount) {
        this.amount = amount;
        return this;
    }

    public AdyenGooglePayAuthoriseRequestFixture withPaymentMethod(AdyenGooglePayPaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        return this;
    }

    public AdyenGooglePayAuthoriseRequestFixture withGoogleBrowserInfo(AdyenBrowserInfo googlePaymentInfo) {
        this.browserInfo = googlePaymentInfo;
        return this;
    }

    public AdyenGooglePayAuthoriseRequestFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }
    
    public AdyenGooglePayAuthorisePayload build() {
        return new AdyenGooglePayAuthorisePayload(merchantAccount, store, reference, amount, paymentMethod, browserInfo, returnUrl);
    }
}
