package uk.gov.pay.connector.gateway.sandbox.applepay;

import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxGatewayResponseGenerator;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.WalletAuthorisationHandler;

public class SandboxWalletAuthorisationHandler implements WalletAuthorisationHandler, SandboxGatewayResponseGenerator {
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) {
        String lastDigitsCardNumber = request.getWalletAuthorisationData().getLastDigitsCardNumber();
        return getSandboxGatewayResponse(lastDigitsCardNumber);
    }

}
