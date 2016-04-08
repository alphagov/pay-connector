package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import static uk.gov.pay.connector.model.CancelGatewayResponse.successfulCancelResponse;

import javax.inject.Inject;
import java.util.Optional;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCancelService extends CardService implements TransactionalGatewayOperation {

    private static ChargeStatus[] legalStatuses = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_READY, CAPTURE_READY
    };

    private static ChargeStatus[] localStatuses = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS
    };
    private final PaymentProviders providers;

    @Inject
    public CardCancelService(ChargeDao chargeDao, PaymentProviders providers) {
        super(chargeDao, providers);
        this.providers = providers;
    }

    public Either<ErrorResponse, GatewayResponse> doCancel(String chargeId, Long accountId) {
        Optional<ChargeEntity> charge = chargeDao
                .findByExternalIdAndGatewayAccount(chargeId, accountId);
        if (charge.isPresent()){
            return globalCancel(charge.get(), ChargeStatus.SYSTEM_CANCELLED);
        } else {
            return chargeNotFound(chargeId);
        }
    }

    Either<ErrorResponse, GatewayResponse> globalCancel(ChargeEntity charge, ChargeStatus status) {
            if (charge.hasStatus(localStatuses)) {
                return localCancel(charge, status);
            } else {
                return TransactionalGatewayOperation.super.executeGatewayOperationFor(charge);
            }
    }

    private Either<ErrorResponse, GatewayResponse> localCancel(ChargeEntity chargeEntity, ChargeStatus status) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        reloadedCharge.setStatus(status);
        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);

        return right(successfulCancelResponse(status));
    }


    @Transactional
    @Override
    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity) {
        return preOperation(chargeEntity, OperationType.CANCELLATION, legalStatuses, ChargeStatus.CANCEL_READY);
    }

    @Override
    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity) {
        return right(getPaymentProviderFor(chargeEntity)
                .cancel(CancelRequest.valueOf(chargeEntity)));
    }

    @Transactional
    @Override
    public Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CancelGatewayResponse cancelResponse = (CancelGatewayResponse) operationResponse;

        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        reloadedCharge.setStatus(cancelResponse.getStatus());

        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);

        return right(operationResponse);
    }

    Either<ErrorResponse, GatewayResponse> chargeNotFound(String chargeId) {
        return left(new ErrorResponse(format("Charge with id [%s] not found.", chargeId), ErrorType.CHARGE_NOT_FOUND));
    }
}
