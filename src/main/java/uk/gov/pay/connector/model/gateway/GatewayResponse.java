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
import static org.apache.commons.lang3.StringUtils.trim;
import static uk.gov.pay.connector.model.GatewayError.baseError;

public class GatewayResponse<T extends BaseResponse> {

    static private final Logger logger = LoggerFactory.getLogger(GatewayResponse.class);

    protected Either<GatewayError, T> response;

    private GatewayResponse(T baseResponse) {
        this.response = right(baseResponse);
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
                e -> Optional.empty(),
                Optional::of
        );
    }

    public Optional<GatewayError> getGatewayError() {
        return response.either(
                Optional::of,
                r -> Optional.empty()
        );
    }

    static public <T extends BaseResponse> GatewayResponse<T> with(T baseResponse) {
        Optional<String> errorCode = getErrorCode(baseResponse);
        Optional<String> errorMessage = getErrorMessage(baseResponse);

        if (errorCode.isPresent() || errorMessage.isPresent()) {
            StringBuilder sb = new StringBuilder();
            sb.append(errorCode.map(e -> format("[%s] ", e)).orElse(""));
            sb.append(errorMessage.orElse(""));
            return GatewayResponse.with(baseError(trim(sb.toString())));
        }

        return new GatewayResponse<>(baseResponse);
    }

    static public <T extends BaseResponse> GatewayResponse<T> with(GatewayError gatewayError) {
        logger.error(format("Error received from gateway: %s", gatewayError));
        return new GatewayResponse<>(gatewayError);
    }
}


