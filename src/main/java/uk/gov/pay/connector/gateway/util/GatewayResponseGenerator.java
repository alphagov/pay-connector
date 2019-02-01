package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class GatewayResponseGenerator {

    public static GatewayResponse getSandboxGatewayResponse(boolean isAuthorised) {
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
            public Optional<GatewayParamsFor3ds> getGatewayParamsFor3ds() {
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
