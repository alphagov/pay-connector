package uk.gov.pay.connector.gateway.sandbox.applepay;

import uk.gov.pay.connector.wallets.applepay.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.WalletAuthorisationHandler;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.CardError;
import uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;

import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxWalletAuthorisationHandler implements WalletAuthorisationHandler {
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) {
        String lastDigitsCardNumber = request.getWalletTemplateData().getLastDigitsCardNumber();        
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();

        //PP-4314 This is duplicated from the "standard" auth in sandbox payment provider, should be extracted when we refactor further to implement the AuthorisationHandler
        if (SandboxCardNumbers.isErrorCard(lastDigitsCardNumber)) {
            CardError errorInfo = SandboxCardNumbers.cardErrorFor(lastDigitsCardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (SandboxCardNumbers.isRejectedCard(lastDigitsCardNumber)) {
            return GatewayResponseGenerator.getSandboxGatewayResponse(false);
        } else if (SandboxCardNumbers.isValidCard(lastDigitsCardNumber)) {
            return GatewayResponseGenerator.getSandboxGatewayResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }

}
