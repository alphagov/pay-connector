package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.model.CancelGatewayResponse;
import uk.gov.pay.connector.model.CancelRequest;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.lang3.BooleanUtils.negate;
import static uk.gov.pay.connector.model.CancelGatewayResponse.successfulCancelResponse;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCancelService extends CardService implements TransactionalGatewayOperation {
    private static final Logger logger = LoggerFactory.getLogger(ChargeService.class);
    public static final String EXPIRY_SUCCESS = "expiry-success";
    public static final String EXPIRY_FAILED = "expiry-failed";
    private static ChargeStatus[] legalStatuses = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUCCESS, AUTHORISATION_READY, CAPTURE_READY
    };

    private static ChargeStatus[] nonGatewayStatuses = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS
    };

    ChargeService chargeService;

    @Inject
    public CardCancelService(ChargeDao chargeDao, PaymentProviders providers, ChargeService chargeService) {
        super(chargeDao, providers);
        this.chargeService = chargeService;
    }

    public GatewayResponse doCancel(String chargeId, Long accountId) {
        Optional<ChargeEntity> charge = chargeDao
                .findByExternalIdAndGatewayAccount(chargeId, accountId);
        if (charge.isPresent()) {
            return cancelCharge(charge.get());
        }
        throw new ChargeNotFoundRuntimeException(chargeId);
    }

    GatewayResponse cancelCharge(ChargeEntity charge) {
        if (charge.hasStatus(nonGatewayStatuses)) {
            return nonGatewayCancel(charge);
        } else {
            return executeGatewayOperationFor(charge);
        }
    }

    public Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<Boolean, List<ChargeEntity>> chargesToProcessExpiry = charges
                .stream()
                .collect(Collectors.partitioningBy(
                        chargeEntity -> ChargeStatus.AUTHORISATION_SUCCESS.getValue().equals(chargeEntity.getStatus())));
        List<ChargeEntity> nonAuthSuccessCharges = chargesToProcessExpiry.get(Boolean.FALSE);
        chargeService.updateStatus(nonAuthSuccessCharges, ChargeStatus.EXPIRED);
        int expiredSuccess = nonAuthSuccessCharges.size();

        List<ChargeEntity> authSuccessCharges = chargesToProcessExpiry.get(Boolean.TRUE);
        Pair<Integer, Integer> successFailPair = expireChargesInAuthorisationSuccess(authSuccessCharges);

        return ImmutableMap.of(EXPIRY_SUCCESS, expiredSuccess + successFailPair.getLeft(), EXPIRY_FAILED, successFailPair.getRight());
    }

    private Pair<Integer, Integer> expireChargesInAuthorisationSuccess(List<ChargeEntity> charges) {
        chargeService.updateStatus(charges, ChargeStatus.EXPIRE_CANCEL_PENDING);

        List<ChargeEntity> successfullyCancelled = new ArrayList<>();
        List<ChargeEntity> failedCancelled = new ArrayList<>();

        charges.stream().forEach(chargeEntity -> {
            GatewayResponse gatewayResponse = doCancel(chargeEntity.getExternalId(), chargeEntity.getGatewayAccount().getId());

            if (responseIsNotSuccessful(gatewayResponse)) {
                logUnsuccessfulResponseReasons(chargeEntity, gatewayResponse);
                failedCancelled.add(chargeEntity);
            } else {
                successfullyCancelled.add(chargeEntity);
            }
        });
        chargeService.updateStatus(successfullyCancelled, ChargeStatus.EXPIRED);
        int expiredSuccess = successfullyCancelled.size();
        chargeService.updateStatus(failedCancelled, ChargeStatus.EXPIRE_CANCEL_FAILED);
        int expireFailed = failedCancelled.size();
        return Pair.of(expiredSuccess, expireFailed);
    }

    protected ChargeStatus getCancelledStatus() {
        return ChargeStatus.SYSTEM_CANCELLED;
    }

    private GatewayResponse nonGatewayCancel(ChargeEntity charge) {
        chargeService.updateStatus(Arrays.asList(charge), getCancelledStatus());
        return successfulCancelResponse(getCancelledStatus());
    }

    @Transactional
    @Override
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        return preOperation(chargeEntity, OperationType.CANCELLATION, legalStatuses, ChargeStatus.CANCEL_READY);
    }

    @Override
    public GatewayResponse operation(ChargeEntity chargeEntity) {
        return getPaymentProviderFor(chargeEntity)
                .cancel(CancelRequest.valueOf(chargeEntity));
    }

    @Override
    public GatewayResponse postOperation(ChargeEntity chargeEntity, GatewayResponse operationResponse) {
        CancelGatewayResponse cancelResponse = (CancelGatewayResponse) operationResponse;
        chargeService.updateStatus(Arrays.asList(chargeEntity), cancelResponse.getStatus());
        return operationResponse;
    }

    private boolean responseIsNotSuccessful(GatewayResponse gatewayResponse) {
        return negate(gatewayResponse.isSuccessful());
    }

    private void logUnsuccessfulResponseReasons(ChargeEntity chargeEntity, GatewayResponse gatewayResponse) {
        if (gatewayResponse.isFailed()) {
            logger.error(format("gateway error: %s %s, while cancelling the charge ID %s",
                    gatewayResponse.getError().getMessage(),
                    gatewayResponse.getError().getErrorType(),
                    chargeEntity.getId()));
        }
        }
}
