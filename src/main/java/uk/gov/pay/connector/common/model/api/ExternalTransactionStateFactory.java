package uk.gov.pay.connector.common.model.api;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.service.payments.commons.model.AuthorisationMode;

public class ExternalTransactionStateFactory {

    public ExternalTransactionState newExternalTransactionState(ChargeEntity chargeEntity) {
        ExternalChargeState externalChargeState = ChargeStatus.fromString(chargeEntity.getStatus()).toExternal();
        String status = externalChargeState.getStatus();
        boolean finished = externalChargeState.isFinished();
        String code = externalChargeState.getCode();
        String message = externalChargeState.getMessage();
        Boolean canRetry = chargeEntity.getCanRetry();

        if (chargeEntity.getAuthorisationMode() == AuthorisationMode.AGREEMENT && canRetry != null) {
            return new ExternalTransactionState(status, finished, code, message, canRetry);
        }

        return new ExternalTransactionState(status, finished, code, message);
    }

}
