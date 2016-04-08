package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;

import java.util.Optional;

import static fj.data.Either.right;

public class UserCardCancelService extends CardCancelService implements TransactionalGatewayOperation  {
    @Inject
    public UserCardCancelService(ChargeDao chargeDao, PaymentProviders providers) {
        super(chargeDao, providers);
    }

    @Transactional
    @Override
    public Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CancelGatewayResponse cancelResponse = (CancelGatewayResponse) operationResponse;

        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

        //TODO: This needs to be thought about when refactoring statuses
        // It would be better if we had three levels of status: GatewayStatus, InternalChargeStatus and
        // ExternalChargeStatus. This would allow us to map GatewayStatus to InternalStaus differently
        // depending on context
        String updatedStatus = (cancelResponse.getStatus().equals(ChargeStatus.SYSTEM_CANCELLED)) ?
                ChargeStatus.USER_CANCELLED.getValue() :
                cancelResponse.getStatus().getValue();

        reloadedCharge.setStatus(ChargeStatus.chargeStatusFrom(updatedStatus));

        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);

        return right(operationResponse);
    }

    public Either<ErrorResponse, GatewayResponse> doCancel(String chargeId) {
        Optional<ChargeEntity> charge = chargeDao
                .findByExternalId(chargeId);

        if (charge.isPresent()) {
            return globalCancel(charge.get(), ChargeStatus.USER_CANCELLED);
        } else {
            return chargeNotFound(chargeId);
        }
    }
}
