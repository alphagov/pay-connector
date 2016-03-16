package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.CancelRequest;
import uk.gov.pay.connector.model.CancelResponse;
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

public class CardCancelService extends CardService {

    private static final ChargeStatus[] CANCELLABLE_STATES = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_READY, CAPTURE_READY
    };

    @Inject
    public CardCancelService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        super(accountDao, chargeDao, providers);
    }

    public Either<GatewayError, GatewayResponse> doCancel(Long chargeId, Long accountId) {
        return chargeDao
                .findByIdAndGatewayAccount(Long.valueOf(chargeId), accountId)
                .map(cancel())
                .orElseGet(chargeNotFound(chargeId));
    }

    private Function<ChargeEntity, Either<GatewayError, GatewayResponse>> cancel() {
        return charge -> hasStatus(charge, CANCELLABLE_STATES) ?
                right(cancelFor(charge)) :
                left(cancelErrorMessageFor(charge.getId(), charge.getStatus()));
    }

    private GatewayResponse cancelFor(ChargeEntity charge) {
        CancelRequest request = CancelRequest.valueOf(charge);
        CancelResponse response = paymentProviderFor(charge).cancel(request);

        if (response.isSuccessful()) {
            charge.setStatus(SYSTEM_CANCELLED);
            chargeDao.mergeAndNotifyStatusHasChanged(charge);
        }
        return response;
    }

    private GatewayError cancelErrorMessageFor(Long chargeId, String status) {
        return baseGatewayError(format("Cannot cancel a charge id [%s]: status is [%s].", chargeId, status));
    }
}
