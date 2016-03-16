package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;

public class CardCaptureService extends CardService {
    private final Logger logger = LoggerFactory.getLogger(CardCaptureService.class);

    @Inject
    public CardCaptureService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        super(accountDao, chargeDao, providers);
    }

    public Either<GatewayError, GatewayResponse> doCapture(String chargeId) {

        Function<ChargeEntity, Either<GatewayError, GatewayResponse>> doCapture =
                (charge) -> {
                    Either<GatewayError, ChargeEntity> preCapture = null;

                    try {
                        preCapture = preCapture(charge);
                    } catch (OptimisticLockException e) {
                        return left(conflictError(format("Capture for charge conflicting, %s", chargeId)));
                    }

                    if (preCapture.isLeft())
                        return left(preCapture.left().value());

                    Either<GatewayError, CaptureResponse> captured =
                            capture(preCapture.right().value());
                    if (captured.isLeft())
                        return left(captured.left().value());

                    Either<GatewayError, GatewayResponse> postCapture =
                            postCapture(preCapture.right().value(), captured.right().value());
                    if (postCapture.isLeft())
                        return left(postCapture.left().value());

                    return right(captured.right().value());
                };

        return chargeDao
                .findByExternalId(chargeId)
                .map(doCapture)
                .orElseGet(chargeNotFound(chargeId));
    }

    @Transactional
    public Either<GatewayError, ChargeEntity> preCapture(ChargeEntity charge) {
        ChargeEntity reloadedCharge = chargeDao.merge(charge);
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


    private Either<GatewayError, CaptureResponse> capture(ChargeEntity charge) {
        CaptureRequest request = CaptureRequest.valueOf(charge);
        CaptureResponse response = paymentProviderFor(charge)
                .capture(request);

        return right(response);
    }

    @Transactional
    public Either<GatewayError, GatewayResponse> postCapture(ChargeEntity charge, CaptureResponse response) {
        ChargeEntity reloadedCharge = chargeDao.merge(charge);
        reloadedCharge.setStatus(response.getStatus());

        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);

        return right(response);
    }
}
