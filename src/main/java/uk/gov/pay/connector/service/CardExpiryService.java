package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.model.CancelRequest;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.transaction.TransactionFlow;

import javax.inject.Inject;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.apache.commons.lang3.BooleanUtils.negate;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardExpiryService {

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
    private Provider<TransactionFlow<ChargeEntity, GatewayResponse, ChargeEntity>> transactionFlowProvider;
    private final ChargeService chargeService;

    @Inject
    public CardExpiryService(ChargeDao chargeDao, PaymentProviders providers,
                             Provider<TransactionFlow<ChargeEntity, GatewayResponse, ChargeEntity>> transactionFlowProvider,
                             ChargeService chargeService) {
        this.chargeDao = chargeDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
        this.chargeService = chargeService;
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
        chargeService.updateStatus(nonAuthSuccessCharges, ChargeStatus.EXPIRED);
        return nonAuthSuccessCharges.size();
    }


    private Pair<Integer, Integer> expireChargesWithCancellation(List<ChargeEntity> gatewayAuthorizedCharges) {
        Map<Boolean, List<ChargeEntity>> processedCharges = gatewayAuthorizedCharges.stream().map(chargeEntity -> transactionFlowProvider.get()
                .startInTx(prepareForExpireCancel(chargeEntity))
                .operationNotInTx(doGatewayCancel())
                .completeInTx(finishExpireCancel())
                .execute())
                .filter(pickIfExistOrWarn())
                .map(Optional::get)
                .collect(Collectors.partitioningBy(processedChargeEntity ->
                        EXPIRED.getValue().equals(processedChargeEntity.getStatus())));
        boolean expireCancelled = true;
        boolean expireCancelFailed = false; //for readability

        return Pair.of(
                processedCharges.get(expireCancelled).size(),
                processedCharges.get(expireCancelFailed).size()
        );
    }

    private Predicate<? super Optional<ChargeEntity>> pickIfExistOrWarn() {
        return optionalEntity -> {
            if (!optionalEntity.isPresent()) {
                logger.error("Transaction did not return a completed entity");
            }
            return optionalEntity.isPresent();
        };
    }

    private Supplier<ChargeEntity> prepareForExpireCancel(ChargeEntity chargeEntity) {
        return () -> {
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

            return reloadedCharge;
        };
    }

    public Function<ChargeEntity, GatewayResponse> doGatewayCancel() {
        return chargeEntity -> providers.resolve(chargeEntity.getGatewayAccount().getGatewayName())
                .cancel(CancelRequest.valueOf(chargeEntity));
    }

    private BiFunction<ChargeEntity, GatewayResponse, ChargeEntity> finishExpireCancel() {
        return (chargeEntity, gatewayResponse) -> {
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
