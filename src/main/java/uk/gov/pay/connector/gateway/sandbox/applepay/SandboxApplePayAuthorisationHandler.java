package uk.gov.pay.connector.gateway.sandbox.applepay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.applepay.ApplePayAuthorisationHandler;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.CardError;
import uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;

import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxApplePayAuthorisationHandler implements ApplePayAuthorisationHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(SandboxApplePayAuthorisationHandler.class);

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(ApplePayAuthorisationGatewayRequest request) {
        LOGGER.info("sandbox apple pay auth");
        String lastDigitsCardNumber = request.getAppleDecryptedPaymentData().getPaymentInfo().getLastDigitsCardNumber();        
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
