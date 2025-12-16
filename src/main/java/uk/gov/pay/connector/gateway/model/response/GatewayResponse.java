package uk.gov.pay.connector.gateway.model.response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

public class GatewayResponse<T extends BaseResponse> {

    private static final Logger logger = LoggerFactory.getLogger(GatewayResponse.class);

    private GatewayError gatewayError;
    private T baseResponse;

    private ProviderSessionIdentifier sessionIdentifier;

    private GatewayResponse(T baseResponse, ProviderSessionIdentifier sessionIdentifier) {
        this.baseResponse = baseResponse;
        this.sessionIdentifier = sessionIdentifier;
    }

    private GatewayResponse(GatewayError error) {
        this.gatewayError = error;
    }

    public Optional<ProviderSessionIdentifier> getSessionIdentifier() {
        return Optional.ofNullable(sessionIdentifier);
    }

    public Optional<T> getBaseResponse() {
        return Optional.ofNullable(baseResponse);
    }

    public Optional<GatewayError> getGatewayError() {
        return Optional.ofNullable(gatewayError);
    }
    
    public void throwGatewayError() throws GatewayException {
        switch (gatewayError.getErrorType()) {
            case GENERIC_GATEWAY_ERROR: throw new GatewayException.GenericGatewayException(gatewayError.getMessage());
            case GATEWAY_ERROR: throw new GatewayException.GatewayErrorException(gatewayError.getMessage());
            case GATEWAY_CONNECTION_TIMEOUT_ERROR: throw new GatewayConnectionTimeoutException(gatewayError.getMessage());
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
        private ProviderSessionIdentifier sessionIdentifier;
        private GatewayError gatewayError;

        private GatewayResponseBuilder() {
        }

        public static <T extends BaseResponse> GatewayResponseBuilder<T> responseBuilder() {
            return new GatewayResponseBuilder<>();
        }

        public GatewayResponseBuilder withResponse(T response) {
            this.response = response;
            return this;
        }

        public GatewayResponseBuilder withSessionIdentifier(ProviderSessionIdentifier responseIdentifier) {
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
