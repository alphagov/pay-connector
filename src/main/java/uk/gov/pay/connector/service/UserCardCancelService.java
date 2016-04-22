package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Optional;

public class UserCardCancelService extends CardCancelService implements TransactionalGatewayOperation  {
    @Inject
    public UserCardCancelService(ChargeDao chargeDao, PaymentProviders providers, ChargeService chargeService) {
        super(chargeDao, providers, chargeService);
    }

    @Override
    public GatewayResponse postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CancelGatewayResponse cancelResponse = (CancelGatewayResponse) operationResponse;

        ChargeStatus updatedStatus;
        if (cancelResponse.isSuccessful()) {
            updatedStatus = getCancelledStatus();

        } else {
            logUnsuccessfulResponseReasons(chargeEntity, operationResponse);
            updatedStatus = ChargeStatus.USER_CANCEL_ERROR;
        }

        chargeService.updateStatus(Arrays.asList(chargeEntity), updatedStatus);
        return operationResponse;
    }

    public GatewayResponse doCancel(String chargeId) {
        Optional<ChargeEntity> charge = chargeDao
                .findByExternalId(chargeId);
        if (charge.isPresent()) {
            return cancelCharge(charge.get());
        }
        throw new ChargeNotFoundRuntimeException(chargeId);
    }

    protected ChargeStatus getCancelledStatus() {
        return ChargeStatus.USER_CANCELLED;
    }
}
