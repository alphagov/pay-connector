package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.ConfirmationDetailsEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus;

public class CardAuthoriseService extends CardService<BaseAuthoriseResponse> implements TransactionalGatewayOperation<BaseAuthoriseResponse> {

    private static ChargeStatus[] legalStates = new ChargeStatus[]{
            ENTERING_CARD_DETAILS
    };

    private Card cardDetails;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao,
                                PaymentProviders providers,
                                CardExecutorService cardExecutorService,
                                ConfirmationDetailsService confirmationDetailsService) {
        super(chargeDao, providers, cardExecutorService, confirmationDetailsService);
    }

    public GatewayResponse doAuthorise(String chargeId, Card cardDetails) {
        this.cardDetails = cardDetails;
        Optional<ChargeEntity> chargeEntity = chargeDao.findByExternalId(chargeId);

        if (chargeEntity.isPresent()) {
            Supplier<GatewayResponse> authorisationSupplier = () -> executeGatewayOperationFor(chargeEntity.get());
            Pair<ExecutionStatus, GatewayResponse> executeResult = cardExecutorService.execute(authorisationSupplier);

            switch (executeResult.getLeft()) {
                case COMPLETED:
                    return executeResult.getRight();
                case IN_PROGRESS:
                    throw new OperationAlreadyInProgressRuntimeException(OperationType.AUTHORISATION.getValue(), chargeId);
                default:
                    throw new GenericGatewayRuntimeException("Exception occurred while doing authorisation");
            }
        } else {
            throw new ChargeNotFoundRuntimeException(chargeId);
        }
    }

    @Transactional
    @Override
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        chargeEntity = preOperation(chargeEntity, OperationType.AUTHORISATION, legalStates, AUTHORISATION_READY);
        getPaymentProviderFor(chargeEntity).generateTransactionId().ifPresent(chargeEntity::setGatewayTransactionId);

        logger.info(format("Card authorisation request sent - charge_external_id=%s, transaction_id=%s, status=%s",
                chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), fromString(chargeEntity.getStatus())));

        return chargeEntity;
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(AuthorisationGatewayRequest.valueOf(chargeEntity, this.cardDetails));
    }

    @Transactional
    @Override
    public GatewayResponse<BaseAuthoriseResponse> postOperation(ChargeEntity chargeEntity, GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

        ChargeStatus status = operationResponse.getBaseResponse()
                .map(response -> response.isAuthorised() ? AUTHORISATION_SUCCESS : AUTHORISATION_REJECTED)
                .orElse(AUTHORISATION_ERROR);
        String transactionId = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId).orElse("");

        logger.info(format("Card authorisation response received - charge_external_id=%s, transaction_id=%s, status=%s",
                chargeEntity.getExternalId(), transactionId, status));
        reloadedCharge.setStatus(status);
        if (StringUtils.isBlank(transactionId)) {
            logger.warn("Card authorisation response received with no transaction id.");
        } else {
            reloadedCharge.setGatewayTransactionId(transactionId);
        }

        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);
        if (status.equals(AUTHORISATION_SUCCESS)) {
            confirmationDetailsService.doStore(reloadedCharge.getExternalId(), cardDetails);
        }
        return operationResponse;
    }
}
