package uk.gov.pay.connector.gateway.requestfactory;

import org.apache.commons.lang3.NotImplementedException;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffApplePayAuthoriseRequest;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;

public class WorldpayOneOffApplePayAuthoriseRequestFactory {

    private final WorldpayAuthoriseRequestFactoryHelper helper;
    
    public WorldpayOneOffApplePayAuthoriseRequestFactory(WorldpayAuthoriseRequestFactoryHelper helper) {
        this.helper = helper;
    }

    public WorldpayOneOffApplePayAuthoriseRequest create(WalletAuthorisationRequest request) {
        throw new NotImplementedException();
    }
}
