package uk.gov.pay.connector.gateway.sandbox.applepay;

import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.CardError;
import uk.gov.pay.connector.gateway.sandbox.SandboxGatewayResponseGenerator;
import uk.gov.pay.connector.gateway.sandbox.SandboxWalletCardNumbers;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.WalletAuthorisationHandler;

import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxWalletAuthorisationHandler implements WalletAuthorisationHandler, SandboxGatewayResponseGenerator {
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) {
        String lastDigitsCardNumber = request.getWalletAuthorisationData().getLastDigitsCardNumber();
        return getSandboxGatewayResponse(lastDigitsCardNumber);
    }

    @Override
    public GatewayResponse getSandboxGatewayResponse(String lastDigitsCardNumber) {
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();

        if (SandboxWalletCardNumbers.isErrorCard(lastDigitsCardNumber)) {
            CardError errorInfo = SandboxWalletCardNumbers.cardErrorFor(lastDigitsCardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (SandboxWalletCardNumbers.isRejectedCard(lastDigitsCardNumber)) {
            return SandboxGatewayResponseGenerator.getSandboxGatewayResponse(false);
        } else if (SandboxWalletCardNumbers.isValidCard(lastDigitsCardNumber)) {
            return SandboxGatewayResponseGenerator.getSandboxGatewayResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }
}
