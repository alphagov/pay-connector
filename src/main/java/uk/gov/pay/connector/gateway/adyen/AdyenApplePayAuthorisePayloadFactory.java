package uk.gov.pay.connector.gateway.adyen;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenApplePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenCredentialsHelper;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenMerchantAccountHelper;
import uk.gov.pay.connector.gateway.model.request.records.AdyenApplePayAuthorisePayload;
import uk.gov.pay.connector.gateway.util.ChargeFrontendUrlHelper;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;

public class AdyenApplePayAuthorisePayloadFactory {

    private final ChargeFrontendUrlHelper chargeFrontendUrlHelper;
    private final AdyenMerchantAccountHelper adyenMerchantAccountHelper;
    private final AdyenCredentialsHelper adyenCredentialsHelper;
    
    @Inject
    public AdyenApplePayAuthorisePayloadFactory(
            AdyenMerchantAccountHelper adyenMerchantAccountHelper, 
            AdyenCredentialsHelper adyenCredentialsHelper,
            ChargeFrontendUrlHelper chargeFrontendUrlHelper
    ) {
        this.chargeFrontendUrlHelper =  chargeFrontendUrlHelper;
        this.adyenMerchantAccountHelper = adyenMerchantAccountHelper;
        this.adyenCredentialsHelper = adyenCredentialsHelper;
    }

    public AdyenApplePayAuthorisePayload create(ApplePayAuthorisationGatewayRequest request) {
        return new AdyenApplePayAuthorisePayload(
                adyenMerchantAccountHelper.getMerchantAccount(request.getGatewayAccount()),
                adyenCredentialsHelper.getStore(request),
                request.getGovUkPayPaymentId(),
                new Amount("GBP", Long.valueOf(request.getAmount())),
                new AdyenApplePayPaymentMethod(request.getApplePayAuthRequest().getPaymentData()),
                chargeFrontendUrlHelper.getFrontendUrlForCharge(request.getGovUkPayPaymentId())
        );
    }

}
