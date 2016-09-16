package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus;

public class CardAuthoriseService extends CardService<BaseAuthoriseResponse> {

    private static ChargeStatus[] legalStates = new ChargeStatus[]{
            ENTERING_CARD_DETAILS
    };

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao,
                                PaymentProviders providers,
                                CardExecutorService cardExecutorService,
                                ConfirmationDetailsService confirmationDetailsService) {
        super(chargeDao, providers, confirmationDetailsService, cardExecutorService);
    }

    public GatewayResponse doAuthorise(String chargeId, Card cardDetails) {

        Optional<ChargeEntity> chargeEntityOptional = chargeDao.findByExternalId(chargeId);

        if (chargeEntityOptional.isPresent()) {
            Supplier<GatewayResponse> authorisationSupplier = () -> {
                ChargeEntity chargeEntity = chargeEntityOptional.get();
                ChargeEntity preOperationResponse;
                try {
                    preOperationResponse = preOperation(chargeEntity);
                } catch (OptimisticLockException e) {
                    throw new ConflictRuntimeException(chargeEntity.getExternalId());
                }

                GatewayResponse<BaseAuthoriseResponse> operationResponse = operation(preOperationResponse, cardDetails);

                return postOperation(preOperationResponse, cardDetails, operationResponse);
            };

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
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        chargeEntity = preOperation(chargeEntity, OperationType.AUTHORISATION, legalStates, AUTHORISATION_READY);
        getPaymentProviderFor(chargeEntity).generateTransactionId().ifPresent(chargeEntity::setGatewayTransactionId);

        logger.info("Card authorisation request sent - charge_external_id={}, transaction_id={}, provider={}, status={}",
                chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), chargeEntity.getGatewayAccount().getGatewayName(), fromString(chargeEntity.getStatus()));

        return chargeEntity;
    }

    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, Card cardDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(AuthorisationGatewayRequest.valueOf(chargeEntity, cardDetails));
    }

    @Transactional
    public GatewayResponse<BaseAuthoriseResponse> postOperation(ChargeEntity chargeEntity, Card cardDetails, GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

        ChargeStatus status = operationResponse.getBaseResponse()
                .map(response -> response.isAuthorised() ? AUTHORISATION_SUCCESS : AUTHORISATION_REJECTED)
                .orElse(AUTHORISATION_ERROR);
        String transactionId = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId).orElse("");

        logger.info("Card authorisation response received - charge_external_id={}, transaction_id={}, status={}",
                chargeEntity.getExternalId(), transactionId, status);

        reloadedCharge.setStatus(status);

        if (StringUtils.isBlank(transactionId)) {
            logger.warn("Card authorisation response received with no transaction id. -  charge_external_id={}", reloadedCharge.getExternalId());
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
