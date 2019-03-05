package uk.gov.pay.connector.gateway.model.response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionTimeoutErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.model.GatewayError;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

public class GatewayResponse<T extends BaseResponse> {

    private static final Logger logger = LoggerFactory.getLogger(GatewayResponse.class);

    private GatewayError gatewayError;
    private T baseResponse;

    private String sessionIdentifier;

    private GatewayResponse(T baseResponse, String sessionIdentifier) {
        this.baseResponse = baseResponse;
        this.sessionIdentifier = sessionIdentifier;
    }

    private GatewayResponse(GatewayError error) {
        this.gatewayError = error;
    }

    @Deprecated
    public boolean isSuccessful() {
        return baseResponse != null;
    }

    @Deprecated
    public boolean isFailed() {
        return gatewayError != null;
    }

    public Optional<String> getSessionIdentifier() {
        return Optional.ofNullable(sessionIdentifier);
    }

    public Optional<T> getBaseResponse() {
        return Optional.ofNullable(baseResponse);
    }

    public Optional<GatewayError> getGatewayError() {
        return Optional.ofNullable(gatewayError);
    }
    
    public void throwGatewayError() throws GatewayErrorException {
        switch (gatewayError.getErrorType()) {
            case GENERIC_GATEWAY_ERROR: throw new GenericGatewayErrorException(gatewayError.getMessage());
            case GATEWAY_CONNECTION_ERROR: throw new GatewayConnectionErrorException(gatewayError.getMessage(), null, "");
            case GATEWAY_CONNECTION_TIMEOUT_ERROR: throw new GatewayConnectionTimeoutErrorException(gatewayError.getMessage());
        }
    }

    @Override
    public String toString() {
        return gatewayError == null
                ? baseResponse.toString()
                : gatewayError.toString();
    }

    public static GatewayResponse with(GatewayError gatewayError) {
        logger.error("Error received from gateway: {}", gatewayError);
        return new GatewayResponse(gatewayError);
    }

    public static class GatewayResponseBuilder<T extends BaseResponse> {
        private T response;
        private String sessionIdentifier;
        private GatewayError gatewayError;

        private GatewayResponseBuilder() {
        }

        public static GatewayResponseBuilder responseBuilder() {
            return new GatewayResponseBuilder();
        }

        public GatewayResponseBuilder withResponse(T response) {
            this.response = response;
            return this;
        }

        public GatewayResponseBuilder withSessionIdentifier(String responseIdentifier) {
            this.sessionIdentifier = responseIdentifier;
            return this;
        }

        public GatewayResponseBuilder withGatewayError(GatewayError gatewayError) {
            this.gatewayError = gatewayError;
            return this;
        }

        public GatewayResponse<T> build() {
            if (gatewayError != null) {
                return new GatewayResponse<>(gatewayError);
            }
            if (StringUtils.isNotBlank(response.getErrorCode()) ||
                    StringUtils.isNotBlank(response.getErrorMessage())) {
                return new GatewayResponse<>(genericGatewayError(response.toString()));
            }
            return new GatewayResponse<>(response, sessionIdentifier);
        }

        public GatewayResponse<T> buildUninterpreted() {
            return new GatewayResponse<>(response, sessionIdentifier);
        }
    }
}
