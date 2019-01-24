package uk.gov.pay.connector.wallets.applepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.model.WalletTemplateData;

public class WalletAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private WalletTemplateData walletTemplateData;

    public WalletAuthorisationGatewayRequest(ChargeEntity charge, WalletTemplateData walletTemplateData) {
        super(charge);
        this.walletTemplateData = walletTemplateData;
    }

    public WalletTemplateData getWalletTemplateData() {
        return walletTemplateData;
    }

    public static WalletAuthorisationGatewayRequest valueOf(ChargeEntity charge, WalletTemplateData applePaymentData) {
        return new WalletAuthorisationGatewayRequest(charge, applePaymentData);
    }
}
