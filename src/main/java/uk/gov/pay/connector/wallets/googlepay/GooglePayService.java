package uk.gov.pay.connector.wallets.googlepay;

import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletAuthoriseService;
import uk.gov.pay.connector.wallets.WalletService;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import javax.inject.Inject;

public class GooglePayService extends WalletService {
    
    @Inject
    public GooglePayService(WalletAuthoriseService authoriseService) {
        super(authoriseService, WalletType.GOOGLE_PAY);
    }
    
}
