package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Card;
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
import static uk.gov.pay.connector.model.GatewayError.illegalStateError;
import static uk.gov.pay.connector.model.GatewayError.operationAlreadyInProgress;
import static uk.gov.pay.connector.model.GatewayErrorType.CHARGE_NOT_FOUND;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class CardAuthoriseService implements TransactionalGatewayOperation {
    private final Logger logger = LoggerFactory.getLogger(CardAuthoriseService.class);

    private final ChargeDao chargeDao;
    private final PaymentProviders providers;

    private Card cardDetails;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao, PaymentProviders providers) {
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    public Either<ErrorResponse, GatewayResponse> doAuthorise(String chargeId, Card cardDetails) {
        this.cardDetails = cardDetails;

        return chargeDao
                .findByExternalId(chargeId)
                .map(TransactionalGatewayOperation.super::executeGatewayOperationFor)
                .orElseGet(chargeNotFound(chargeId));
    }

    @Transactional
    @Override
    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        if (hasStatus(reloadedCharge, ChargeStatus.EXPIRED)) {
            return left(chargeExpired(format("Cannot authorise charge as it is expired, %s", reloadedCharge.getExternalId())));
        }
        if (!hasStatus(reloadedCharge, ENTERING_CARD_DETAILS)) {
            if (hasStatus(reloadedCharge, AUTHORISATION_READY)) {
                return left(operationAlreadyInProgress(format("Authorisation for charge already in progress, %s",
                        reloadedCharge.getExternalId())));
            }
            logger.error(format("Charge with id [%s] and with status [%s] should be in [ENTERING CARD DETAILS] for authorisation.",
                    reloadedCharge.getId(), reloadedCharge.getStatus()));
            return left(illegalStateError(format("Charge not in correct state to be processed, %s", reloadedCharge.getExternalId())));
        }
        reloadedCharge.setStatus(AUTHORISATION_READY);

        return right(reloadedCharge);
    }

    @Override
    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity) {
        AuthorisationRequest request = new AuthorisationRequest(chargeEntity, this.cardDetails);
        AuthorisationResponse response = paymentProviderFor(chargeEntity)
                .authorise(request);

        return right(response);
    }

    @Transactional
    @Override
    public Either<ErrorResponse, GatewayResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        AuthorisationResponse authorisationResponse = (AuthorisationResponse) operationResponse;

        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
        reloadedCharge.setStatus(authorisationResponse.getNewChargeStatus());
        reloadedCharge.setGatewayTransactionId(authorisationResponse.getTransactionId());

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
