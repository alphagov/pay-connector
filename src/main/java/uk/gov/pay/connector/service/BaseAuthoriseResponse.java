package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.ChargeStatus;

public interface BaseAuthoriseResponse extends BaseResponse {

    String getTransactionId();

    boolean isAuthorised();

    AuthoriseStatus authoriseStatus();

    enum AuthoriseStatus {
        AUTHORISED(ChargeStatus.AUTHORISATION_SUCCESS),
        REJECTED(ChargeStatus.AUTHORISATION_REJECTED),
        REQUIRES_3D(ChargeStatus.AUTHORISATION_3DS_REQUIRED),
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
