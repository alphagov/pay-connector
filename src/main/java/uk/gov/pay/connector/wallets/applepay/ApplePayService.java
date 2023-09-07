package uk.gov.pay.connector.wallets.applepay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletAuthoriseService;
import uk.gov.pay.connector.wallets.WalletService;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import javax.inject.Inject;

public class ApplePayService extends WalletService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplePayService.class);

    @Inject
    public ApplePayService(WalletAuthoriseService authoriseService) {
        super(authoriseService, WalletType.APPLE_PAY);
    }
}
