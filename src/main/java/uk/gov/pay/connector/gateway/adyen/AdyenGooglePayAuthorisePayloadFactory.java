package uk.gov.pay.connector.gateway.adyen;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenGooglePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenCredentialsHelper;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenMerchantAccountHelper;
import uk.gov.pay.connector.gateway.model.request.records.AdyenGooglePayAuthorisePayload;
import uk.gov.pay.connector.gateway.util.ChargeFrontendUrlHelper;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.AdyenGooglePayAuthRequest;

public class AdyenGooglePayAuthorisePayloadFactory {

    private final ChargeFrontendUrlHelper chargeFrontendUrlHelper;
    private final AdyenMerchantAccountHelper adyenMerchantAccountHelper;
    private final AdyenCredentialsHelper adyenCredentialsHelper;

    @Inject
    public AdyenGooglePayAuthorisePayloadFactory(
            AdyenMerchantAccountHelper adyenMerchantAccountHelper,
            AdyenCredentialsHelper adyenCredentialsHelper,
            ChargeFrontendUrlHelper chargeFrontendUrlHelper
    ) {
        this.chargeFrontendUrlHelper =  chargeFrontendUrlHelper;
        this.adyenMerchantAccountHelper = adyenMerchantAccountHelper;
        this.adyenCredentialsHelper = adyenCredentialsHelper;
    }

    public AdyenGooglePayAuthorisePayload create(GooglePayAuthorisationGatewayRequest request) {
        AdyenGooglePayAuthRequest googlePayAuthRequest = request.getAdyenGooglePayAuthRequest();
        
        return new AdyenGooglePayAuthorisePayload(
                adyenMerchantAccountHelper.getMerchantAccount(request.getGatewayAccount()),
                adyenCredentialsHelper.getStore(request),
                request.getGovUkPayPaymentId(),
                new Amount("GBP", Long.valueOf(request.getAmount())),
                new AdyenGooglePayPaymentMethod(googlePayAuthRequest.token()),
                new AdyenBrowserInfoFactory().create(googlePayAuthRequest.getPaymentInfo()),
                chargeFrontendUrlHelper.getFrontendUrlForCharge(request.getGovUkPayPaymentId())
        );
    }

}
