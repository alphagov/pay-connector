package uk.gov.pay.connector.wallets.applepay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletAuthoriseService;
import uk.gov.pay.connector.wallets.WalletService;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import javax.inject.Inject;

public class ApplePayService extends WalletService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplePayService.class);

    private ApplePayDecrypter applePayDecrypter;

    @Inject
    public ApplePayService(ApplePayDecrypter applePayDecrypter, WalletAuthoriseService authoriseService) {
        super(authoriseService, WalletType.APPLE_PAY);
        this.applePayDecrypter = applePayDecrypter;
    }

    @Override
    public WalletAuthorisationData getWalletAuthorisationData(String chargeId, WalletAuthorisationRequest walletAuthorisationRequest, String paymentGatewayName) {
        LOGGER.info("Decrypting apple pay payload for charge with id {}", chargeId);
        ApplePayAuthRequest applePayAuthRequest = (ApplePayAuthRequest) walletAuthorisationRequest;
        AppleDecryptedPaymentData result = paymentGatewayName.equals(PaymentGatewayName.STRIPE.getName()) ?
                new AppleDecryptedPaymentData() :
                applePayDecrypter.performDecryptOperation(applePayAuthRequest);
        result.setPaymentInfo(walletAuthorisationRequest.getPaymentInfo());
        LOGGER.info("Finished decryption for id {}", chargeId);
        return result;
    }
}
