package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static uk.gov.pay.connector.model.ErrorResponse.chargeExpired;
import static uk.gov.pay.connector.model.ErrorResponse.illegalStateError;
import static uk.gov.pay.connector.model.ErrorResponse.operationAlreadyInProgress;
import static uk.gov.pay.connector.model.ErrorResponseType.CHARGE_NOT_FOUND;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;

public class CardCaptureService implements TransactionalGatewayOperation {
    private final Logger logger = LoggerFactory.getLogger(CardCaptureService.class);

    private final ChargeDao chargeDao;
    private final PaymentProviders providers;

    @Inject
    public CardCaptureService(ChargeDao chargeDao, PaymentProviders providers) {
        this.chargeDao = chargeDao;
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
    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity charge) {
        ChargeEntity reloadedCharge = chargeDao.merge(charge);
        if (hasStatus(reloadedCharge, ChargeStatus.EXPIRED)) {
            return left(chargeExpired(format("Cannot capture charge as it is expired, %s", reloadedCharge.getExternalId())));
        }
        if (!hasStatus(reloadedCharge, AUTHORISATION_SUCCESS)) {
            if (hasStatus(reloadedCharge, CAPTURE_READY)) {
                return left(operationAlreadyInProgress(format("Capture for charge already in progress, %s",
                        reloadedCharge.getExternalId())));
            }
            logger.error(format("Charge with id [%s] and with status [%s] should be in [AUTHORISATION SUCCESS] for capture.",
                    reloadedCharge.getId(), reloadedCharge.getStatus()));
            return left(illegalStateError(format("Charge not in correct state to be processed, %s", reloadedCharge.getExternalId())));
        }
        reloadedCharge.setStatus(CAPTURE_READY);

        return right(reloadedCharge);
    }

    @Override
    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity) {
        CaptureRequest request = CaptureRequest.valueOf(chargeEntity);
        CaptureResponse response = paymentProviderFor(chargeEntity)
                .capture(request);

        return right(response);
    }

    @Transactional
    @Override
    public Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CaptureResponse captureResponse = (CaptureResponse) operationResponse;

        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        reloadedCharge.setStatus(captureResponse.getStatus());

        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);

        return right(operationResponse);
    }

    public PaymentProvider paymentProviderFor(ChargeEntity charge) {
        return providers.resolve(charge.getGatewayAccount().getGatewayName());
    }

    public boolean hasStatus(ChargeEntity charge, ChargeStatus... states) {
        return Arrays.stream(states)
                .anyMatch(status -> equalsIgnoreCase(status.getValue(), charge.getStatus()));
    }

    public Supplier<Either<ErrorResponse, GatewayResponse>> chargeNotFound(String chargeId) {
        return () -> left(new ErrorResponse(format("Charge with id [%s] not found.", chargeId), ErrorType.CHARGE_NOT_FOUND));
    }
}
