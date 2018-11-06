package uk.gov.pay.connector.gateway.model.response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.GatewayError;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.model.GatewayError.baseError;

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

    public boolean isSuccessful() {
        return baseResponse != null;
    }

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

    @Override
    public String toString() {
        if (isFailed()) {
            return gatewayError.toString();
        } else {
            return baseResponse.toString();
        }
    }

    public static <T extends BaseResponse> GatewayResponse<T> with(GatewayError gatewayError) {
        logger.error("Error received from gateway: {}", gatewayError);
        return new GatewayResponse<>(gatewayError);
    }

    public static class GatewayResponseBuilder<T extends BaseResponse> {
        private T response;
        private String sessionIdentifier;
        private GatewayError gatewayError;

        private GatewayResponseBuilder() {
        }

        public static <T extends BaseResponse> GatewayResponseBuilder<T> responseBuilder() {
            return new GatewayResponseBuilder<>();
        }

        public GatewayResponseBuilder<T> withResponse(T response) {
            this.response = response;
            return this;
        }

        public GatewayResponseBuilder<T> withSessionIdentifier(String responseIdentifier) {
            this.sessionIdentifier = responseIdentifier;
            return this;
        }

        public GatewayResponseBuilder<T> withGatewayError(GatewayError gatewayError) {
            this.gatewayError = gatewayError;
            return this;
        }

        public GatewayResponse<T> build() {
            if (gatewayError != null) {
                return new GatewayResponse<>(gatewayError);
            }
            if (StringUtils.isNotBlank(response.getErrorCode()) ||
                    StringUtils.isNotBlank(response.getErrorMessage())) {
                return new GatewayResponse<>(baseError(response.toString()));
            }
            return new GatewayResponse<>(response, sessionIdentifier);
        }
    }
}
