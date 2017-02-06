package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.GenericGatewayRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.Optional;
import java.util.function.Supplier;

import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.CardExecutorService.ExecutionStatus;

public class CardAuthoriseService extends CardService<BaseAuthoriseResponse> {

    private static ChargeStatus[] legalStates = new ChargeStatus[]{
            ENTERING_CARD_DETAILS
    };

    private final CardExecutorService cardExecutorService;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao,
                                PaymentProviders providers,
                                CardExecutorService cardExecutorService,
                                MetricRegistry metricRegistry) {
        super(chargeDao, providers, metricRegistry);

        this.cardExecutorService = cardExecutorService;
    }

    public GatewayResponse doAuthorise(String chargeId, AuthorisationDetails authorisationDetails) {

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

                GatewayResponse<BaseAuthoriseResponse> operationResponse = operation(preOperationResponse, authorisationDetails);

                return postOperation(preOperationResponse, authorisationDetails, operationResponse);
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
        return chargeEntity;
    }

    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, AuthorisationDetails authorisationDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(AuthorisationGatewayRequest.valueOf(chargeEntity, authorisationDetails));
    }

    @Transactional
    public GatewayResponse<BaseAuthoriseResponse> postOperation(ChargeEntity chargeEntity, AuthorisationDetails authorisationDetails, GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

        ChargeStatus status = operationResponse.getBaseResponse()
                .map(response -> response.isAuthorised() ? AUTHORISATION_SUCCESS : AUTHORISATION_REJECTED)
                .orElse(AUTHORISATION_ERROR);
        String transactionId = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId).orElse("");

        logger.info("AuthorisationDetails authorisation response received - charge_external_id={}, operation_type={}, transaction_id={}, status={}",
                chargeEntity.getExternalId(), OperationType.AUTHORISATION.getValue(), transactionId, status);

        GatewayAccountEntity account = chargeEntity.getGatewayAccount();

        metricRegistry.counter(String.format("gateway-operations.%s.%s.authorise.result.%s", account.getGatewayName(), account.getType(), status.toString())).inc();
        metricRegistry.counter(String.format("service-operations.%s.%s.authorise.result.%s", account.getServiceName(), account.getType(), status.toString())).inc();

        reloadedCharge.setStatus(status);

        if (StringUtils.isBlank(transactionId)) {
            logger.warn("AuthorisationDetails authorisation response received with no transaction id. -  charge_external_id={}", reloadedCharge.getExternalId());
        } else {
            reloadedCharge.setGatewayTransactionId(transactionId);
        }

        appendCardDetails(reloadedCharge, authorisationDetails);
        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge, Optional.empty());
        return operationResponse;
    }

    private void appendCardDetails(ChargeEntity chargeEntity, AuthorisationDetails authorisationDetails) {
        CardDetailsEntity detailsEntity = new CardDetailsEntity();
        detailsEntity.setCardBrand(authorisationDetails.getCardBrand());
        detailsEntity.setBillingAddress(new AddressEntity(authorisationDetails.getAddress()));
        detailsEntity.setCardHolderName(authorisationDetails.getCardHolder());
        detailsEntity.setExpiryDate(authorisationDetails.getEndDate());
        detailsEntity.setLastDigitsCardNumber(StringUtils.right(authorisationDetails.getCardNo(), 4));
        chargeEntity.setCardDetails(detailsEntity);
        logger.info("Stored confirmation details for charge - charge_external_id={}", chargeEntity.getExternalId());
    }
}
