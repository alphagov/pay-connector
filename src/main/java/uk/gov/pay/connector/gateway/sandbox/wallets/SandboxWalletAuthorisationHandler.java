package uk.gov.pay.connector.gateway.sandbox.wallets;

import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxGatewayResponseGenerator;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;

public class SandboxWalletAuthorisationHandler {

    private SandboxGatewayResponseGenerator sandboxGatewayResponseGenerator;
    
    public SandboxWalletAuthorisationHandler(SandboxGatewayResponseGenerator sandboxGatewayResponseGenerator) {
        this.sandboxGatewayResponseGenerator = sandboxGatewayResponseGenerator;
    }
    
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest request) {
        return sandboxGatewayResponseGenerator.getSandboxGatewayWalletResponse(request.description());
    }

    public GatewayResponse<BaseAuthoriseResponse> authoriseGooglePay(GooglePayAuthorisationGatewayRequest request) {
        return sandboxGatewayResponseGenerator.getSandboxGatewayWalletResponse(request.description());
    }
    
}
