package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class CardAuthoriseService extends CardService implements TransactionalGatewayOperation {

    private static ChargeStatus[] legalStates = new ChargeStatus[]{
            ENTERING_CARD_DETAILS
    };

    private final PaymentProviders providers;

    private Card cardDetails;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao, PaymentProviders providers) {
        super(chargeDao);
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
        return preOperation(chargeEntity, OperationType.AUTHORISATION, legalStates, ChargeStatus.AUTHORISATION_READY);
    }

    @Override
    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity) {
        AuthorisationRequest request = new AuthorisationRequest(chargeEntity, this.cardDetails);
        String gatewayName = chargeEntity.getGatewayAccount().getGatewayName();
        AuthorisationResponse response = providers.resolve(gatewayName)
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

    public Supplier<Either<ErrorResponse, GatewayResponse>> chargeNotFound(String chargeId) {
        return () -> left(new ErrorResponse(format("Charge with id [%s] not found.", chargeId), ErrorType.CHARGE_NOT_FOUND));
    }

}
