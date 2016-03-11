package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureService extends CardService {

    @Inject
    public CardCaptureService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        super(accountDao, chargeDao, providers);
    }

    public Either<GatewayError, GatewayResponse> doCapture(Long chargeId) {
        return chargeDao
                .findById(Long.valueOf(chargeId))
                .map(capture())
                .orElseGet(chargeNotFound(chargeId));
    }

    private Function<ChargeEntity, Either<GatewayError, GatewayResponse>> capture() {
        return charge -> hasStatus(charge, AUTHORISATION_SUCCESS) ?
                right(captureFor(charge)) :
                left(captureErrorMessageFor(charge.getStatus()));
    }

    private GatewayResponse captureFor(ChargeEntity charge) {
        CaptureRequest request = CaptureRequest.valueOf(charge);
        CaptureResponse response = paymentProviderFor(charge)
                .capture(request);

        ChargeStatus newStatus =
                response.isSuccessful() ?
                        CAPTURE_SUBMITTED :
                        CAPTURE_UNKNOWN;

        charge.setStatus(newStatus);
        chargeDao.mergeAndNotifyStatusHasChanged(charge);

        return response;
    }

    private GatewayError captureErrorMessageFor(String currentStatus) {
        return baseGatewayError(format("Cannot capture a charge with status %s.", currentStatus));
    }
}
