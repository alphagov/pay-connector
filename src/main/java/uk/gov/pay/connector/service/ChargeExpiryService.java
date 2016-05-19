package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.CancelRequest;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.transaction.*;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.apache.commons.lang3.BooleanUtils.negate;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeExpiryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String EXPIRY_SUCCESS = "expiry-success";
    public static final String EXPIRY_FAILED = "expiry-failed";

    public static final ChargeStatus[] EXPIRABLE_STATUSES =
            new ChargeStatus[]{
                    CREATED,
                    ENTERING_CARD_DETAILS,
                    AUTHORISATION_SUCCESS
            };
    private final ChargeDao chargeDao;
    private final PaymentProviders providers;
    private Provider<TransactionFlow> transactionFlowProvider;

    @Inject
    public ChargeExpiryService(ChargeDao chargeDao, PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
    }

    public Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<Boolean, List<ChargeEntity>> chargesToProcessExpiry = charges
                .stream()
                .collect(Collectors.partitioningBy(chargeEntity ->
                        ChargeStatus.AUTHORISATION_SUCCESS.getValue().equals(chargeEntity.getStatus())));

        int expiredSuccess = expireChargesWithCancellationNotRequired(chargesToProcessExpiry.get(Boolean.FALSE));
        Pair<Integer, Integer> expireWithCancellationResult = expireChargesWithCancellation(chargesToProcessExpiry.get(Boolean.TRUE));

        return ImmutableMap.of(EXPIRY_SUCCESS, expiredSuccess + expireWithCancellationResult.getLeft(), EXPIRY_FAILED, expireWithCancellationResult.getRight());
    }

    private int expireChargesWithCancellationNotRequired(List<ChargeEntity> nonAuthSuccessCharges) {
        List<ChargeEntity> processedEntities = nonAuthSuccessCharges.stream().map(chargeEntity -> transactionFlowProvider.get()
                .executeNext((TransactionalOperation<TransactionContext, ChargeEntity>) (context -> {
                    logger.info("charge status to update - from: " + chargeEntity.getStatus() + ", to: " + EXPIRED + " for Charge ID: " + chargeEntity.getId());
                    chargeEntity.setStatus(EXPIRED);
                    return chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
                }))
                .complete().get(ChargeEntity.class))
                .collect(Collectors.toList());
        return processedEntities.size();
    }


    private Pair<Integer, Integer> expireChargesWithCancellation(List<ChargeEntity> gatewayAuthorizedCharges) {

        final List<ChargeEntity> expireCancelled = newArrayList();
        final List<ChargeEntity> expireCancelFailed = newArrayList();
        final List<ChargeEntity> unexpectedStatuses = newArrayList();

        gatewayAuthorizedCharges.forEach(chargeEntity -> {
            ChargeEntity processedEntity = transactionFlowProvider.get()
                    .executeNext(prepareForExpireCancel(chargeEntity))
                    .executeNext(doGatewayCancel())
                    .executeNext(finishExpireCancel())
                    .complete().get(ChargeEntity.class);

            if (processedEntity == null) {
                //this shouldn't happen, but don't break the expiry job
                logger.error("Transaction context did not return a processed ChargeEntity during expiry of charge {}", chargeEntity.getExternalId());
            } else {
                if (EXPIRED.getValue().equals(processedEntity.getStatus())) {
                    expireCancelled.add(processedEntity);
                } else if (EXPIRE_CANCEL_FAILED.getValue().equals(processedEntity.getStatus())) {
                    expireCancelFailed.add(processedEntity);
                } else {
                    unexpectedStatuses.add(processedEntity); //this shouldn't happen, but still don't break the expiry job
                }
            }
        });

        unexpectedStatuses.forEach(chargeEntity ->
                logger.error("ChargeEntity with id {} returned with unexpected status {} during expiry", chargeEntity.getExternalId(), chargeEntity.getStatus())
        );

        return Pair.of(
                expireCancelled.size(),
                expireCancelFailed.size()
        );
    }

    private TransactionalOperation<TransactionContext, ChargeEntity> prepareForExpireCancel(ChargeEntity chargeEntity) {
        return context -> {
            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

            if (!reloadedCharge.hasStatus(EXPIRABLE_STATUSES)) {
                if (reloadedCharge.hasStatus(EXPIRE_CANCEL_PENDING)) {
                    throw new OperationAlreadyInProgressRuntimeException("Expiration", reloadedCharge.getExternalId());
                }
                logger.error(format("Charge with id [%s] and with status [%s] should be in one of the following legal states, [%s]",
                        reloadedCharge.getId(), reloadedCharge.getStatus(), getLegalStatusNames(EXPIRABLE_STATUSES)));
                throw new IllegalStateRuntimeException(reloadedCharge.getExternalId());
            }
            reloadedCharge.setStatus(EXPIRE_CANCEL_PENDING);
            return chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge);
        };
    }

    private NonTransactionalOperation<TransactionContext, GatewayResponse> doGatewayCancel() {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            return providers.resolve(chargeEntity.getGatewayAccount().getGatewayName())
                    .cancel(CancelRequest.valueOf(chargeEntity));

        };
    }

    private TransactionalOperation<TransactionContext, ChargeEntity> finishExpireCancel() {
        return context -> {
            ChargeEntity chargeEntity = context.get(ChargeEntity.class);
            GatewayResponse gatewayResponse = context.get(CancelGatewayResponse.class);
            ChargeStatus status;
            if (responseIsNotSuccessful(gatewayResponse)) {
                logUnsuccessfulResponseReasons(chargeEntity, gatewayResponse);
                status = EXPIRE_CANCEL_FAILED;
            } else {
                status = EXPIRED;
            }
            logger.info("charge status to update - from: " + chargeEntity.getStatus() + ", to: " + status + " for Charge ID: " + chargeEntity.getId());
            chargeEntity.setStatus(status);
            return chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity);
        };
    }

    private void logUnsuccessfulResponseReasons(ChargeEntity chargeEntity, GatewayResponse gatewayResponse) {
        if (gatewayResponse.isFailed()) {
            logger.error(format("gateway error: %s %s, while cancelling the charge ID %s",
                    gatewayResponse.getError().getMessage(),
                    gatewayResponse.getError().getErrorType(),
                    chargeEntity.getId()));
        }
    }

    private String getLegalStatusNames(ChargeStatus[] legalStatuses) {
        return Stream.of(legalStatuses).map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }

    private boolean responseIsNotSuccessful(GatewayResponse gatewayResponse) {
        return negate(gatewayResponse.isSuccessful());
    }
}
