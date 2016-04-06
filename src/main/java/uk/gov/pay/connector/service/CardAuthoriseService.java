package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.resources.CardExecutorService;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.resources.CardExecutorService.ExecutionStatus;

public class CardAuthoriseService extends CardService implements TransactionalGatewayOperation {

    private static final Logger logger = LoggerFactory.getLogger(CardAuthoriseService.class);

    private static ChargeStatus[] legalStates = new ChargeStatus[]{
            ENTERING_CARD_DETAILS
    };

    private Card cardDetails;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao, PaymentProviders providers, CardExecutorService cardExecutorService) {
        super(chargeDao, providers, cardExecutorService);
    }

    public Either<ErrorResponse, GatewayResponse> doAuthorise(String chargeId, Card cardDetails) {
        this.cardDetails = cardDetails;
        Optional<ChargeEntity> chargeEntity = chargeDao.findByExternalId(chargeId);

        if (chargeEntity.isPresent()) {
            Supplier<Either<ErrorResponse, GatewayResponse>> authorisationSupplier = () -> TransactionalGatewayOperation.super.executeGatewayOperationFor(chargeEntity.get());
            Pair<ExecutionStatus, Either<ErrorResponse, GatewayResponse>> executeResult = cardExecutorService.execute(chargeId, authorisationSupplier);

            switch (executeResult.getLeft()) {
                case COMPLETED:
                    return executeResult.getRight();
                case IN_PROGRESS:
                    return right(inProgressGatewayResponse(ChargeStatus.chargeStatusFrom(chargeEntity.get().getStatus()), chargeId));
                default:
                    return left(new ErrorResponse("Exception occurred while doing authorisation", ErrorType.GENERIC_GATEWAY_ERROR));
            }
        } else {
            return chargeNotFound(chargeId).get();
        }
    }

    @Transactional
    @Override
    public Either<ErrorResponse, ChargeEntity> preOperation(ChargeEntity chargeEntity) {
        return preOperation(chargeEntity, OperationType.AUTHORISATION, legalStates, ChargeStatus.AUTHORISATION_READY);
    }

    @Override
    public Either<ErrorResponse, GatewayResponse> operation(ChargeEntity chargeEntity) {
        return right(getPaymentProviderFor(chargeEntity)
                .authorise(AuthorisationRequest.valueOf(chargeEntity, this.cardDetails)));
    }

    private GatewayResponse inProgressGatewayResponse(ChargeStatus chargeStatus, String id) {
        return new AuthorisationResponse(false, null, chargeStatus, id, true);
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
