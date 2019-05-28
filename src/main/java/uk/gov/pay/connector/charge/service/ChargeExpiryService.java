package uk.gov.pay.connector.charge.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ChargeSweepConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus.AuthorisationStage;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;
import uk.gov.pay.connector.token.dao.TokenDao;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus.AuthorisationStage.DURING_AUTHORISATION;
import static uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus.AuthorisationStage.POST_AUTHORISATION;
import static uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus.AuthorisationStage.PRE_AUTHORISATION;
import static uk.gov.pay.connector.charge.service.StatusFlow.EXPIRE_FLOW;

public class ChargeExpiryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String EXPIRY_SUCCESS = "expiry-success";
    private static final String EXPIRY_FAILED = "expiry-failed";
    private static final long DAYS_TILL_EXPIRY = -7;

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final TokenDao tokenDao;
    private final PaymentProviders providers;
    private final QueryService queryService;
    
    private final ChargeSweepConfig chargeSweepConfig;

    @Inject
    public ChargeExpiryService(ChargeDao chargeDao,
                               ChargeEventDao chargeEventDao,
                               TokenDao tokenDao,
                               PaymentProviders providers,
                               QueryService queryService,
                               ConnectorConfiguration config) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.tokenDao = tokenDao;
        this.providers = providers;
        this.chargeSweepConfig = config.getChargeSweepConfig();
        this.queryService = queryService;
    }

    Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<AuthorisationStage, List<ChargeEntity>> chargesGroupedByAuthStage = charges
                .stream()
                .collect(Collectors.groupingBy(this::getAuthorisationStage));

        Map<Boolean, List<ChargeEntity>> chargesPartitionedByNeedForExpiryWithGateway = 
                getNullSafeList(chargesGroupedByAuthStage.get(DURING_AUTHORISATION))
                        .stream()
                        .collect(Collectors.partitioningBy(this::isExpirableWithGateway));
        
        List<ChargeEntity> toExpireWithoutGateway = new ImmutableList.Builder<ChargeEntity>()
                .addAll(getNullSafeList(chargesGroupedByAuthStage.get(PRE_AUTHORISATION)))
                .addAll(getNullSafeList(chargesPartitionedByNeedForExpiryWithGateway.get(Boolean.FALSE)))
                .build();
        int expiredSuccess = expireChargesWithoutGateway(toExpireWithoutGateway);

        List<ChargeEntity> toExpireWithGateway = new ImmutableList.Builder<ChargeEntity>()
                .addAll(getNullSafeList(chargesGroupedByAuthStage.get(POST_AUTHORISATION)))
                .addAll(getNullSafeList(chargesPartitionedByNeedForExpiryWithGateway.get(Boolean.TRUE)))
                .build();
        Pair<Integer, Integer> expireWithCancellationResult = expireChargesWithGateway(toExpireWithGateway);

        return ImmutableMap.of(
                EXPIRY_SUCCESS, expiredSuccess + expireWithCancellationResult.getLeft(),
                EXPIRY_FAILED, expireWithCancellationResult.getRight()
        );
    }

    private List<ChargeEntity> getNullSafeList(List<ChargeEntity> charges) {
        return Optional.ofNullable(charges)
                .orElseGet(Collections::emptyList);
    }

    private AuthorisationStage getAuthorisationStage(ChargeEntity chargeEntity) {
        return ExpirableChargeStatus.of(ChargeStatus.fromString(chargeEntity.getStatus())).getAuthorisationStage();
    }

    private boolean isExpirableWithGateway(ChargeEntity charge) {
        try {
            return queryService
                    .getChargeGatewayStatus(charge)
                    .getMappedStatus()
                    .map(chargeStatus -> !chargeStatus.toExternal().isFinished())
                    .orElse(false);
        } catch (WebApplicationException | UnsupportedOperationException | GatewayException | IllegalArgumentException e) {
            logger.info("Unable to retrieve status for charge {}: {}", charge.getExternalId(), e.getMessage());
            return false;
        }
    }

    public Map<String, Integer> sweepAndExpireCharges() {
        List<ChargeEntity> chargesToExpire = new ImmutableList.Builder<ChargeEntity>()
                .addAll(getChargesToExpireWithRegularExpiryThreshold())
                .addAll(getChargesToExpireWithDelayedExpiryThreshold())
                .build();
        
        deleteTokensOlderThanSpecifiedDate();
        logger.info("Charges found for expiry - number_of_charges={}, since_date={}, awaiting_capture_date{}",
                chargesToExpire.size(), getExpiryDateForRegularCharges(), getExpiryDateForAwaitingCaptureRequest());
        
        return expire(chargesToExpire);
    }
    
    private int deleteTokensOlderThanSpecifiedDate() {
        ZonedDateTime cut_off_date = ZonedDateTime.now(ZoneId.of("UTC")).plusDays(DAYS_TILL_EXPIRY);
        return tokenDao.deleteTokensOlderThanSpecifiedDate(cut_off_date);
    }
    

    private List<ChargeEntity> getChargesToExpireWithDelayedExpiryThreshold() {
        return chargeDao.findBeforeDateWithStatusIn(getExpiryDateForAwaitingCaptureRequest(),
                ExpirableChargeStatus.getValuesAsStream()
                        .filter(ExpirableChargeStatus::isDelayedThresholdType)
                        .map(ExpirableChargeStatus::getChargeStatus)
                        .collect(Collectors.toList()));
    }

    private List<ChargeEntity> getChargesToExpireWithRegularExpiryThreshold() {
        return chargeDao.findBeforeDateWithStatusIn(getExpiryDateForRegularCharges(),
                ExpirableChargeStatus.getValuesAsStream()
                        .filter(ExpirableChargeStatus::isRegularThresholdType)
                        .map(ExpirableChargeStatus::getChargeStatus)
                        .collect(Collectors.toList()));
    }

    private int expireChargesWithoutGateway(List<ChargeEntity> nonAuthSuccessCharges) {
        List<ChargeEntity> processedEntities = nonAuthSuccessCharges
                .stream().map(chargeEntity -> changeStatusTo(chargeEntity.getExternalId(), EXPIRED))
                .collect(Collectors.toList());

        return processedEntities.size();
    }

    private Pair<Integer, Integer> expireChargesWithGateway(List<ChargeEntity> gatewayAuthorizedCharges) {

        final List<ChargeEntity> expireCancelled = newArrayList();
        final List<ChargeEntity> expireCancelFailed = newArrayList();

        gatewayAuthorizedCharges.forEach(chargeEntity -> {

            ChargeEntity processedEntity = prepareForTermination(chargeEntity.getExternalId());
            ChargeStatus newStatus;

            try {
                GatewayResponse<BaseCancelResponse> gatewayResponse = doGatewayCancel(processedEntity);
                newStatus = determineTerminalState(gatewayResponse);
            } catch (GatewayException e) {
                newStatus= EXPIRE_FLOW.getFailureTerminalState();
                logger.error("Gateway error while cancelling the Charge - charge_external_id={}, gateway_error={}",
                        chargeEntity.getExternalId(), e.getMessage());
            }

            ChargeEntity expiredCharge = changeStatusTo(processedEntity.getExternalId(), newStatus);

            if (EXPIRED.getValue().equals(expiredCharge.getStatus())) {
                expireCancelled.add(processedEntity);
            } else if (EXPIRE_CANCEL_FAILED.getValue().equals(expiredCharge.getStatus())) {
                expireCancelFailed.add(expiredCharge);
            }
        });

        return Pair.of(
                expireCancelled.size(),
                expireCancelFailed.size()
        );
    }

    private ChargeStatus determineTerminalState(GatewayResponse<BaseCancelResponse> cancelResponse) {
        return cancelResponse.getBaseResponse().map(response -> {
            switch (response.cancelStatus()) {
                case CANCELLED:
                    return EXPIRE_FLOW.getSuccessTerminalState();
                case SUBMITTED:
                    return EXPIRE_FLOW.getSubmittedState();
                default:
                    return EXPIRE_FLOW.getFailureTerminalState();
            }
        }).orElse(EXPIRE_FLOW.getFailureTerminalState());
    }
    
    public GatewayResponse<BaseCancelResponse> forceCancelWithGateway(ChargeEntity charge) {
        try {
            return doGatewayCancel(charge);
        } catch (GatewayException e) {
            throw new WebApplicationException(String.format(
                    "Unable to cancel charge %s with gateway: %s",
                    charge.getExternalId(),
                    e.getMessage()));
        }
    }
    
    private ZonedDateTime getExpiryDateForRegularCharges() {
        int chargeExpiryWindowSeconds = chargeSweepConfig.getDefaultChargeExpiryThreshold();
        logger.debug("Charge expiry window size in seconds: [{}]", chargeExpiryWindowSeconds);
        return ZonedDateTime.now().minusSeconds(chargeExpiryWindowSeconds);
    }

    private ZonedDateTime getExpiryDateForAwaitingCaptureRequest() {
        int chargeExpiryWindowSeconds = chargeSweepConfig.getAwaitingCaptureExpiryThreshold();
        logger.debug("Charge expiry window size for awaiting_delay_capture in seconds: [{}]", chargeExpiryWindowSeconds);
        return ZonedDateTime.now().minusSeconds(chargeExpiryWindowSeconds);
    }

    private static String getLegalStatusNames(List<ChargeStatus> legalStatuses) {
        return legalStatuses.stream().map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }

    private GatewayResponse<BaseCancelResponse> doGatewayCancel(ChargeEntity chargeEntity) throws GatewayException {
        return providers.byName(chargeEntity.getPaymentGatewayName()).cancel(CancelGatewayRequest.valueOf(chargeEntity));
    }

    // Only methods marked as public will be picked up by GuicePersist
    @Transactional
    @SuppressWarnings("WeakerAccess")
    public ChargeEntity changeStatusTo(String chargeId, ChargeStatus targetStatus) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {
                    logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                            chargeEntity.getExternalId(), chargeEntity.getStatus(), targetStatus);
                    chargeEntity.setStatus(targetStatus);
                    chargeEventDao.persistChargeEventOf(chargeEntity);
                    return chargeEntity;
                })
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public ChargeEntity prepareForTermination(String chargeId) {
        return chargeDao.findByExternalId(chargeId).map(chargeEntity -> {
            ChargeStatus newStatus = EXPIRE_FLOW.getLockState();
            final ChargeStatus chargeStatus = ChargeStatus.fromString(chargeEntity.getStatus());
            if (!EXPIRE_FLOW.getTerminatableStatuses().contains(chargeStatus)) {
                if (newStatus.equals(chargeStatus)) {
                    throw new OperationAlreadyInProgressRuntimeException(EXPIRE_FLOW.getName(), chargeId);
                } else if (Arrays.asList(AUTHORISATION_READY, AUTHORISATION_3DS_READY).contains(chargeStatus)) {
                    throw new ConflictRuntimeException(chargeEntity.getExternalId());
                }

                logger.error("Charge is not in one of the legal states. charge_external_id={}, status={}, legal_states={}",
                        chargeId, chargeEntity.getStatus(), getLegalStatusNames(EXPIRE_FLOW.getTerminatableStatuses()));

                throw new IllegalStateRuntimeException(chargeId);
            }
            chargeEntity.setStatus(newStatus);

            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();

            // Used by Sumo Logic saved search
            logger.info("Card cancel request sent - charge_external_id={}, charge_status={}, account_id={}, transaction_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                    chargeEntity.getExternalId(),
                    chargeStatus,
                    gatewayAccount.getId(),
                    chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getAmount(),
                    OperationType.CANCELLATION.getValue(),
                    gatewayAccount.getGatewayName(),
                    gatewayAccount.getType(),
                    newStatus);

            chargeEventDao.persistChargeEventOf(chargeEntity);

            return chargeEntity;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }
}
