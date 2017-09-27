package uk.gov.pay.connector.model.gateway;

import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.service.BaseResponse;

import java.util.Optional;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseError;

public class GatewayResponse<T extends BaseResponse> {

    static private final Logger logger = LoggerFactory.getLogger(GatewayResponse.class);

    protected Either<GatewayError, T> response;

    private String sessionIdentifier;

    private GatewayResponse(T baseResponse, String sessionIdentifier) {
        this.response = right(baseResponse);
        this.sessionIdentifier = sessionIdentifier;
    }

    private GatewayResponse(GatewayError error) {
        this.response = left(error);
    }

    public boolean isSuccessful() {
        return response.isRight();
    }

    public boolean isFailed() {
        return response.isLeft();
    }

    public Optional<String> getSessionIdentifier() {
        return Optional.ofNullable(sessionIdentifier);
    }

    static public <T extends BaseResponse> Optional<String> getErrorCode(T baseResponse) {
        return checkIfEmpty(baseResponse.getErrorCode());
    }

    static public <T extends BaseResponse> Optional<String> getErrorMessage(T baseResponse) {
        return checkIfEmpty(baseResponse.getErrorMessage());
    }

    static public Optional<String> checkIfEmpty(String string) {
        if (StringUtils.isBlank(string)) {
            return Optional.empty();
        }
        return Optional.ofNullable(string);
    }

    public Optional<T> getBaseResponse() {
        return response.either(
                e -> Optional.<T>empty(),
                Optional::<T>of
        );
    }

    public Optional<GatewayError> getGatewayError() {
        return response.either(
                Optional::<GatewayError>of,
                r -> Optional.<GatewayError>empty()
        );
    }

    @Override
    public String toString() {
        return response.either(GatewayError::toString, T::toString);
    }

    static public <T extends BaseResponse> GatewayResponse<T> with(GatewayError gatewayError) {
        logger.error(format("Error received from gateway: %s", gatewayError));
        return new GatewayResponse<>(gatewayError);
    }

    public static class GatewayResponseBuilder<T extends BaseResponse> {
        private T response;
        private String sessionIdentifier;
        private GatewayError gatewayError;

        private GatewayResponseBuilder() {
        }

        public static <T extends BaseResponse> GatewayResponseBuilder<T> responseBuilder() {
            return new GatewayResponseBuilder<T>();
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
            if(gatewayError != null) {
                return new GatewayResponse<>(gatewayError);
            }

            Optional<String> errorCode = getErrorCode(response);
            Optional<String> errorMessage = getErrorMessage(response);

            if (errorCode.isPresent() || errorMessage.isPresent()) {
                return new GatewayResponse<>(baseError(response.toString()));
            }

            return new GatewayResponse<>(response, sessionIdentifier);
        }
    }
}


