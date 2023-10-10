package uk.gov.pay.connector.gateway.sandbox.wallets;

import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxGatewayResponseGenerator;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

public class SandboxWalletAuthorisationHandler {

    private SandboxGatewayResponseGenerator sandboxGatewayResponseGenerator;
    
    public SandboxWalletAuthorisationHandler(SandboxGatewayResponseGenerator sandboxGatewayResponseGenerator) {
        this.sandboxGatewayResponseGenerator = sandboxGatewayResponseGenerator;
    }
    
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest request) {
        return sandboxGatewayResponseGenerator.getSandboxGatewayWalletResponse(request.getDescription());
    }

    public GatewayResponse<BaseAuthoriseResponse> authoriseGooglePay(GooglePayAuthorisationGatewayRequest request) {
        return authoriseWallet(request.getGooglePayAuthRequest().getPaymentInfo());
    }
    
    private GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletPaymentInfo walletPaymentInfo) {
        String lastDigitsCardNumber = walletPaymentInfo.getLastDigitsCardNumber();
        return sandboxGatewayResponseGenerator.getSandboxGatewayResponse(lastDigitsCardNumber);
    }
}
