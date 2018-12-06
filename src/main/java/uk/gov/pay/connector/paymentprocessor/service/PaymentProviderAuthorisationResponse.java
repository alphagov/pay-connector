package uk.gov.pay.connector.paymentprocessor.service;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;

public class PaymentProviderAuthorisationResponse {
    private static final Logger logger = LoggerFactory.getLogger(PaymentProviderAuthorisationResponse.class);

    private String transactionId;
    private ChargeStatus chargeStatus;
    private Auth3dsDetailsEntity auth3dsDetailsEntity;
    private String sessionIdentifier;
    private GatewayError gatewayError;
    private BaseAuthoriseResponse.AuthoriseStatus authoriseStatus;

    private PaymentProviderAuthorisationResponse(String transactionId, ChargeStatus chargeStatus, Auth3dsDetailsEntity auth3dsDetailsEntity, String sessionIdentifier, GatewayError gatewayError, BaseAuthoriseResponse.AuthoriseStatus authoriseStatus) {
        this.transactionId = transactionId;
        this.chargeStatus = chargeStatus;
        this.auth3dsDetailsEntity = auth3dsDetailsEntity;
        this.sessionIdentifier = sessionIdentifier;
        this.gatewayError = gatewayError;
        this.authoriseStatus = authoriseStatus;
    }


    public Optional<String> getTransactionId() {
        return Optional.ofNullable(this.transactionId);
    }

    public ChargeStatus getChargeStatus() {
        return this.chargeStatus;
    }

    public Optional<Auth3dsDetailsEntity> getAuth3dsDetailsEntity() {
        return Optional.ofNullable(this.auth3dsDetailsEntity);
    }

    public Optional<String> getSessionIdentifier() {
        return Optional.ofNullable(this.sessionIdentifier);
    }

    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return Optional.ofNullable(authoriseStatus);
    }

    public boolean isSuccessful() {
        return gatewayError == null;
    }

    public static PaymentProviderAuthorisationResponse from(String chargeExternalId, GatewayResponse<BaseAuthoriseResponse> response) {
        return new PaymentProviderAuthorisationResponse(
                extractTransactionId(chargeExternalId, response),
                extractChargeStatus(response),
                extract3dsDetails(response).orElse(null),
                response.getSessionIdentifier().orElse(null),
                response.getGatewayError().orElse(null),
                extractAuthoriseStatus(response));
    }

    public static PaymentProviderAuthorisationResponse from(GatewayResponse<BaseAuthoriseResponse> response) {
        return from(null, response);
    }

    private static BaseAuthoriseResponse.AuthoriseStatus extractAuthoriseStatus(GatewayResponse<BaseAuthoriseResponse> response) {
        return response.getBaseResponse().map(BaseAuthoriseResponse::authoriseStatus).orElse(null);
    }

    public Optional<GatewayError> getGatewayError() {
        return Optional.ofNullable(this.gatewayError);
    }

    private static String extractTransactionId(String chargeExternalId, GatewayResponse<BaseAuthoriseResponse> response) {
        String transactionId = response.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId)
                .orElse(null);

        if (StringUtils.isBlank(transactionId)) {
            logger.warn("AuthCardDetails authorisation response received with no transaction id. -  charge_external_id={}",
                    chargeExternalId);
        }

        return transactionId;
    }

    private static ChargeStatus extractChargeStatus(GatewayResponse<BaseAuthoriseResponse> response) {
        return response.getBaseResponse()
                .map(BaseAuthoriseResponse::authoriseStatus)
                .map(BaseAuthoriseResponse.AuthoriseStatus::getMappedChargeStatus)
                .orElseGet(() -> response.getGatewayError()
                        .map(PaymentProviderAuthorisationResponse::mapError)
                        .orElse(ChargeStatus.AUTHORISATION_ERROR));
    }

    private static ChargeStatus mapError(GatewayError gatewayError) {
        switch (gatewayError.getErrorType()) {
            case GENERIC_GATEWAY_ERROR:
                return AUTHORISATION_ERROR;
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                return AUTHORISATION_TIMEOUT;
            default:
                return AUTHORISATION_UNEXPECTED_ERROR;
        }
    }

    private static Optional<Auth3dsDetailsEntity> extract3dsDetails(GatewayResponse<BaseAuthoriseResponse> response) {
        return response.getBaseResponse()
                .flatMap(BaseAuthoriseResponse::getGatewayParamsFor3ds)
                .map(GatewayParamsFor3ds::toAuth3dsDetailsEntity);
    }
}
