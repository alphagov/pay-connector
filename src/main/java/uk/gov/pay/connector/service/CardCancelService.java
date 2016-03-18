package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static uk.gov.pay.connector.model.ErrorResponse.baseErrorResponse;
import static uk.gov.pay.connector.model.ErrorResponse.chargeExpired;
import static uk.gov.pay.connector.model.ErrorResponseType.CHARGE_NOT_FOUND;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCancelService implements TransactionalGatewayOperation {

    private static final ChargeStatus[] CANCELLABLE_STATES = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_READY, CAPTURE_READY
    };

    private final GatewayAccountDao accountDao;
    private final ChargeDao chargeDao;
    private final PaymentProviders providers;

    @Inject
    public CardCancelService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    public Either<ErrorResponse, GatewayResponse> doCancel(String chargeId, Long accountId) {
        Optional<ChargeEntity> charge = chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId);
        if(charge.isPresent() && hasStatus(charge.get(), EXPIRED)) {
            return left(chargeExpired(format("Cannot cancel a charge id [%s]: status is [%s].", charge.get().getExternalId(), EXPIRED.getValue())));

        }
        return charge.map(TransactionalGatewayOperation.super::executeGatewayOperationFor)
                .orElseGet(chargeNotFound(chargeId));
    }

    @Transactional
    @Override
    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity) {
        return hasStatus(chargeEntity, CANCELLABLE_STATES) ?
                right(chargeEntity) :
                left(cancelErrorMessageFor(chargeEntity.getExternalId(), chargeEntity.getStatus()));
    }

    @Override
    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity) {
        CancelRequest cancelRequest = CancelRequest.valueOf(chargeEntity);
        CancelResponse cancelResponse = paymentProviderFor(chargeEntity).cancel(cancelRequest);
        return right(cancelResponse);
    }

    @Transactional
    @Override
    public Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CancelResponse cancelResponse = (CancelResponse) operationResponse;

        if (cancelResponse.isSuccessful()) {
            chargeEntity.setStatus(SYSTEM_CANCELLED);
            chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
        }
        return right(operationResponse);
    }

    private ErrorResponse cancelErrorMessageFor(String chargeId, String status) {
        return ErrorResponse.baseError(format("Cannot cancel a charge id [%s]: status is [%s].", chargeId, status));
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
