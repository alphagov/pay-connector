package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;

import java.util.Map;

public class WalletAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private WalletAuthorisationData walletAuthorisationData;

    public WalletAuthorisationGatewayRequest(ChargeEntity charge, WalletAuthorisationData walletAuthorisationData) {
        super(charge);
        this.walletAuthorisationData = walletAuthorisationData;
    }

    public WalletAuthorisationData getWalletAuthorisationData() {
        return walletAuthorisationData;
    }

    public static WalletAuthorisationGatewayRequest valueOf(ChargeEntity charge, WalletAuthorisationData applePaymentData) {
        return new WalletAuthorisationGatewayRequest(charge, applePaymentData);
    }

    @Override
    public Map<String, String> getGatewayCredentials() {
        return charge.getGatewayAccountCredentialsEntity().getCredentials();
    }
}
