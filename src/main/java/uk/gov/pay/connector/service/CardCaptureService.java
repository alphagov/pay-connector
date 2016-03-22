package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;

public class CardCaptureService extends CardService implements TransactionalGatewayOperation {

    private static ChargeStatus[] legalStatuses = new ChargeStatus[]{
            AUTHORISATION_SUCCESS
    };

    private final PaymentProviders providers;

    @Inject
    public CardCaptureService(ChargeDao chargeDao, PaymentProviders providers) {
        super(chargeDao);
        this.providers = providers;
    }

    public Either<ErrorResponse, GatewayResponse> doCapture(String chargeId) {
        return chargeDao
                .findByExternalId(chargeId)
                .map(TransactionalGatewayOperation.super::executeGatewayOperationFor)
                .orElseGet(chargeNotFound(chargeId));
    }
    @Transactional
    @Override
    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity) {
        return preOperation(chargeEntity, CardService.OperationType.CAPTURE, legalStatuses, ChargeStatus.CAPTURE_READY);
    }

    @Override
    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity) {
        CaptureRequest request = CaptureRequest.valueOf(chargeEntity);
        String gatewayName = chargeEntity.getGatewayAccount().getGatewayName();
        CaptureResponse response = providers.resolve(gatewayName).capture(request);
        return right(response);
    }

    @Transactional
    @Override
    public Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CaptureResponse captureResponse = (CaptureResponse) operationResponse;

        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        reloadedCharge.setStatus(captureResponse.getStatus());

        chargeDao.notifyStatusHasChanged(reloadedCharge);

        return right(operationResponse);
    }

    public Supplier<Either<ErrorResponse, GatewayResponse>> chargeNotFound(String chargeId) {
        return () -> left(new ErrorResponse(format("Charge with id [%s] not found.", chargeId), ErrorType.CHARGE_NOT_FOUND));
    }
}
