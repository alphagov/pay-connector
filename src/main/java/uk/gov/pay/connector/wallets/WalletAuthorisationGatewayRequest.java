package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;

public class WalletAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private WalletAuthorisationRequest walletAuthorisationRequest;

    public WalletAuthorisationGatewayRequest(ChargeEntity charge, WalletAuthorisationRequest walletAuthorisationRequest) {
        super(charge);
        this.walletAuthorisationRequest = walletAuthorisationRequest;
    }

    public WalletAuthorisationRequest getWalletAuthorisationRequest() {
        return walletAuthorisationRequest;
    }

    public static WalletAuthorisationGatewayRequest valueOf(ChargeEntity charge, WalletAuthorisationRequest walletAuthorisationRequest) {
        return new WalletAuthorisationGatewayRequest(charge, walletAuthorisationRequest);
    }
}
