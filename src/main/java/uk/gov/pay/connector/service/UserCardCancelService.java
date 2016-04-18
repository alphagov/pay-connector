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

import static java.lang.String.format;

public class UserCardCancelService extends CardCancelService implements TransactionalGatewayOperation  {
    @Inject
    public UserCardCancelService(ChargeDao chargeDao, PaymentProviders providers, ChargeService chargeService) {
        super(chargeDao, providers, chargeService);
    }

    @Override
    public GatewayResponse postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CancelGatewayResponse cancelResponse = (CancelGatewayResponse) operationResponse;

        //TODO: This needs to be thought about when refactoring statuses
        // It would be better if we had three levels of status: GatewayStatus, InternalChargeStatus and
        // ExternalChargeStatus. This would allow us to map GatewayStatus to InternalStaus differently
        // depending on context
        ChargeStatus updatedStatus = (cancelResponse.getStatus().equals(ChargeStatus.SYSTEM_CANCELLED)) ?
                getCancelledStatus() :
                cancelResponse.getStatus();

        chargeService.updateStatus(Arrays.asList(chargeEntity), updatedStatus);
        return operationResponse;
    }

    public GatewayResponse doCancel(String chargeId) {
        Optional<ChargeEntity> charge = chargeDao
                .findByExternalId(chargeId);
        if (charge.isPresent()) {
            return cancelCharge(charge.get());
        }
        throw new ChargeNotFoundRuntimeException(format("Charge with id [%s] not found.", chargeId));
    }

    protected ChargeStatus getCancelledStatus() {
        return ChargeStatus.USER_CANCELLED;
    }
}
