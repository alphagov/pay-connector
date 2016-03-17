package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.function.Function;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class CardAuthoriseService extends CardService {
    private final Logger logger = LoggerFactory.getLogger(CardAuthoriseService.class);

    @Inject
    public CardAuthoriseService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        super(accountDao, chargeDao, providers);
    }

    public Either<ErrorResponse, GatewayResponse> doAuthorise(String chargeId, Card cardDetails) {

        Function<ChargeEntity, Either<ErrorResponse, GatewayResponse>> doAuthorise =
                (charge) -> {
                    Either<ErrorResponse, ChargeEntity> preAuthorised = null;

                    try {
                        preAuthorised = preAuthorise(charge);
                    } catch (OptimisticLockException e) {
                        return left(conflictError(format("Authorisation for charge conflicting, %s", chargeId)));
                    }

                    if (preAuthorised.isLeft())
                        return left(preAuthorised.left().value());

                    Either<ErrorResponse, AuthorisationResponse> authorised =
                            authorise(preAuthorised.right().value(), cardDetails);
                    if (authorised.isLeft())
                        return left(authorised.left().value());

                    Either<ErrorResponse, GatewayResponse> postAuthorised =
                            postAuthorise(preAuthorised.right().value(), authorised.right().value());
                    if (postAuthorised.isLeft())
                        return left(postAuthorised.left().value());

                    return right(authorised.right().value());
                };

        return chargeDao
                .findByExternalId(chargeId)
                .map(doAuthorise)
                .orElseGet(chargeNotFound(chargeId));
    }

    @Transactional
    public Either<ErrorResponse, ChargeEntity> preAuthorise(ChargeEntity charge) {
        ChargeEntity reloadedCharge = chargeDao.merge(charge);
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

    private Either<ErrorResponse, AuthorisationResponse> authorise(ChargeEntity charge, Card cardDetails) {
        AuthorisationRequest request = new AuthorisationRequest(charge, cardDetails);
        AuthorisationResponse response = paymentProviderFor(charge)
                .authorise(request);

        return right(response);
    }

    @Transactional
    public Either<ErrorResponse, GatewayResponse> postAuthorise(ChargeEntity charge, AuthorisationResponse response) {
        ChargeEntity reloadedCharge = chargeDao.merge(charge);
        reloadedCharge.setStatus(response.getNewChargeStatus());
        reloadedCharge.setGatewayTransactionId(response.getTransactionId());

        return right(response);
    }
}
