package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.ChargeStatus;

public interface BaseAuthoriseResponse extends BaseResponse {

    String getTransactionId();

    AuthoriseStatus authoriseStatus();

    String get3dsPaRequest();

    String get3dsIssuerUrl();

    enum AuthoriseStatus {
        AUTHORISED(ChargeStatus.AUTHORISATION_SUCCESS),
        REJECTED(ChargeStatus.AUTHORISATION_REJECTED),
        REQUIRES_3DS(ChargeStatus.AUTHORISATION_3DS_REQUIRED),
        CANCELLED(ChargeStatus.AUTHORISATION_CANCELLED),
        ERROR(ChargeStatus.AUTHORISATION_ERROR);

        ChargeStatus mappedChargeStatus;

        AuthoriseStatus(ChargeStatus status) {
            mappedChargeStatus = status;
        }

        public ChargeStatus getMappedChargeStatus() {
            return mappedChargeStatus;
        }
    }

}
