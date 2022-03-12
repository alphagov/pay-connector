package uk.gov.pay.connector.gateway.sandbox;

import io.dropwizard.auth.Auth;
import uk.gov.pay.connector.events.model.charge.GatewayTimeoutDuringAuthorisation;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public interface SandboxGatewayResponseGenerator {
    
    default GatewayResponse getSandboxGatewayResponse(String lastDigitsCardNumber) {
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        
        if (SandboxCardNumbers.isErrorCard(lastDigitsCardNumber)) {
            CardError errorInfo = SandboxCardNumbers.cardErrorFor(lastDigitsCardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (SandboxCardNumbers.isRejectedCard(lastDigitsCardNumber)) {
            return getSandboxGatewayResponse(false);
        } else if (SandboxCardNumbers.isValidCard(lastDigitsCardNumber)) {
            if (SandboxCardNumbers.GOOD_CARD_AUTH_DELAY.equals(lastDigitsCardNumber)) {
                try { Thread.sleep(10000); } catch (InterruptedException ignored) { }
            }
            return getSandboxGatewayResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }

    static GatewayResponse getSandboxGatewayResponse(boolean isAuthorised) {
        GatewayResponse.GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseAuthoriseResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public AuthoriseStatus authoriseStatus() {
                return isAuthorised ? AuthoriseStatus.AUTHORISED : AuthoriseStatus.REJECTED;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public Optional<Gateway3dsRequiredParams> getGatewayParamsFor3ds() {
                return Optional.empty();
            }

            @Override
            public String toString() {
                return "Sandbox authorisation response (transactionId: " + getTransactionId()
                        + ", isAuthorised: " + isAuthorised + ')';
            }
        }).build();
    }
}
