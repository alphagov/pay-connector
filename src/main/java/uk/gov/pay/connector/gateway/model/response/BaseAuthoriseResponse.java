package uk.gov.pay.connector.gateway.model.response;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

import java.util.Map;
import java.util.Optional;

public interface BaseAuthoriseResponse extends BaseResponse {

    String getTransactionId();

    AuthoriseStatus authoriseStatus();

    Optional<? extends Gateway3dsRequiredParams> getGatewayParamsFor3ds();

    default Optional<Auth3dsRequiredEntity> extractAuth3dsRequiredDetails() {
        return getGatewayParamsFor3ds().map(Gateway3dsRequiredParams::toAuth3dsRequiredEntity);
    }
    
    default Optional<Map<String, String>> getGatewayRecurringAuthToken() {
        return Optional.empty();
    }

    enum AuthoriseStatus {
        SUBMITTED(ChargeStatus.AUTHORISATION_SUBMITTED),
        AUTHORISED(ChargeStatus.AUTHORISATION_SUCCESS),
        REJECTED(ChargeStatus.AUTHORISATION_REJECTED),
        REQUIRES_3DS(ChargeStatus.AUTHORISATION_3DS_REQUIRED),
        AUTH_3DS_READY(ChargeStatus.AUTHORISATION_3DS_READY),
        CANCELLED(ChargeStatus.AUTHORISATION_CANCELLED),
        ERROR(ChargeStatus.AUTHORISATION_ERROR),
        EXCEPTION(ChargeStatus.AUTHORISATION_ERROR);

        ChargeStatus mappedChargeStatus;

        AuthoriseStatus(ChargeStatus status) {
            mappedChargeStatus = status;
        }

        public ChargeStatus getMappedChargeStatus() {
            return mappedChargeStatus;
        }
    }

}
