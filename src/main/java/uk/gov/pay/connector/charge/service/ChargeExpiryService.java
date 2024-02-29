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
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidForceStateTransitionException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.paymentprocessor.service.QueryService;
import uk.gov.pay.connector.token.dao.TokenDao;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus.AuthorisationStage.DURING_AUTHORISATION;
import static uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus.AuthorisationStage.POST_AUTHORISATION;
import static uk.gov.pay.connector.charge.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class ChargeExpiryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String EXPIRY_SUCCESS = "expiry-success";
    private static final String EXPIRY_FAILED = "expiry-failed";
    private final ChargeDao chargeDao;
    private final ChargeService chargeService;
    private final TokenDao tokenDao;
    private final IdempotencyDao idempotencyDao;
    private final PaymentProviders providers;
    private final QueryService queryService;
    private final ChargeSweepConfig chargeSweepConfig;
    private final Clock clock;

    @Inject
    public ChargeExpiryService(ChargeDao chargeDao,
                               ChargeService chargeService,
                               TokenDao tokenDao,
                               IdempotencyDao idempotencyDao,
                               PaymentProviders providers,
                               QueryService queryService,
                               ConnectorConfiguration config,
                               Clock clock) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.tokenDao = tokenDao;
        this.idempotencyDao = idempotencyDao;
        this.providers = providers;
        this.chargeSweepConfig = config.getChargeSweepConfig();
        this.queryService = queryService;
        this.clock = clock;
    }

    private enum ExpiryMethod {
        EXPIRE_WITHOUT_GATEWAY,
        EXPIRE_WITH_GATEWAY,
        CHECK_STATUS_WITH_GATEWAY_BEFORE_EXPIRING
    }

    Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<ExpiryMethod, List<ChargeEntity>> chargesGroupedByExpiryMethod = charges
                .stream()
                .collect(Collectors.groupingBy(this::getExpiryMethod));

        int expiredWithoutGatewaySuccess = expireChargesWithoutGateway(getNullSafeList(chargesGroupedByExpiryMethod.get(ExpiryMethod.EXPIRE_WITHOUT_GATEWAY)));
        Pair<Integer, Integer> expireWithCancellationResult = expireChargesWithGateway(getNullSafeList(chargesGroupedByExpiryMethod.get(ExpiryMethod.EXPIRE_WITH_GATEWAY)));
        Pair<Integer, Integer> expireOrForceTransitionResult = expireChargesOrPotentiallyForceTransitionState(getNullSafeList(chargesGroupedByExpiryMethod.get(ExpiryMethod.CHECK_STATUS_WITH_GATEWAY_BEFORE_EXPIRING)));

        return ImmutableMap.of(
                EXPIRY_SUCCESS, expiredWithoutGatewaySuccess + expireWithCancellationResult.getLeft() + expireOrForceTransitionResult.getLeft(),
                EXPIRY_FAILED, expireWithCancellationResult.getRight() + expireOrForceTransitionResult.getRight()
        );
    }

    private ExpiryMethod getExpiryMethod(ChargeEntity chargeEntity) {
        var authorisationStage = getAuthorisationStage(chargeEntity);
        if ((authorisationStage == DURING_AUTHORISATION || authorisationStage == POST_AUTHORISATION)
                && queryService.canQueryChargeGatewayStatus(chargeEntity.getPaymentGatewayName())) {
            return ExpiryMethod.CHECK_STATUS_WITH_GATEWAY_BEFORE_EXPIRING;
        } else if (authorisationStage == POST_AUTHORISATION) {
            return ExpiryMethod.EXPIRE_WITH_GATEWAY;
        } else {
            return ExpiryMethod.EXPIRE_WITHOUT_GATEWAY;
        }
    }

    private List<ChargeEntity> getNullSafeList(List<ChargeEntity> charges) {
        return Optional.ofNullable(charges)
                .orElseGet(Collections::emptyList);
    }

    private AuthorisationStage getAuthorisationStage(ChargeEntity chargeEntity) {
        return ExpirableChargeStatus.of(ChargeStatus.fromString(chargeEntity.getStatus())).getAuthorisationStage();
    }

    public Map<String, Integer> sweepAndExpireChargesAndTokensAndIdempotencyKeys() {
        List<ChargeEntity> chargesToExpire = new ImmutableList.Builder<ChargeEntity>()
                .addAll(getChargesToExpireWithRegularExpiryThreshold())
                .addAll(getChargesToExpireWithDelayedExpiryThreshold())
                .build();
        Instant tokenExpiryThreshold = getExpiryThresholdForTokens();
        int numberOfTokensDeleted = deleteTokensOlderThanSpecifiedDate(tokenExpiryThreshold);
        Instant idempotencyExpiryThreshold = clock.instant().minus(chargeSweepConfig.getIdempotencyKeyExpiryThresholdInSeconds());
        int numberOfIdempotencyKeysDeleted = idempotencyDao.deleteIdempotencyKeysOlderThanSpecifiedDateTime(idempotencyExpiryThreshold);
        logger.info("Tokens deleted - number_of_tokens={}, since_date={}", numberOfTokensDeleted, tokenExpiryThreshold);
        logger.info("Idempotency keys deleted - number_of_idempotency_keys={}, since_date={}", numberOfIdempotencyKeysDeleted, idempotencyExpiryThreshold);
        logger.info("Charges found for expiry - number_of_charges={}, since_date={}, updated_before={}, awaiting_capture_date={}",
                chargesToExpire.size(), getExpiryDateForRegularCharges(), getDateToExpireChargesUpdatedBefore(),
                getExpiryDateForAwaitingCaptureRequest());

        return expire(chargesToExpire);
    }

    private int deleteTokensOlderThanSpecifiedDate(Instant tokenExpiryDate) {
        return tokenDao.deleteTokensOlderThanSpecifiedDate(tokenExpiryDate.atZone(ZoneId.of("UTC")));
    }

    private List<ChargeEntity> getChargesToExpireWithDelayedExpiryThreshold() {
        return chargeDao.findBeforeDateWithStatusIn(getExpiryDateForAwaitingCaptureRequest(),
                ExpirableChargeStatus.getValuesAsStream()
                        .filter(ExpirableChargeStatus::isDelayedThresholdType)
                        .map(ExpirableChargeStatus::getChargeStatus)
                        .collect(Collectors.toList()));
    }

    private List<ChargeEntity> getChargesToExpireWithRegularExpiryThreshold() {
        return chargeDao.findChargesByCreatedUpdatedDatesAndWithStatusIn(getExpiryDateForRegularCharges(),
                getDateToExpireChargesUpdatedBefore(),
                ExpirableChargeStatus.getValuesAsStream()
                        .filter(ExpirableChargeStatus::isRegularThresholdType)
                        .map(ExpirableChargeStatus::getChargeStatus)
                        .collect(Collectors.toList()));
    }

    private int expireChargesWithoutGateway(List<ChargeEntity> nonAuthSuccessCharges) {
        List<ChargeEntity> processedEntities = nonAuthSuccessCharges
                .stream().map(chargeEntity -> chargeService.transitionChargeState(chargeEntity.getExternalId(), EXPIRED))
                .collect(Collectors.toList());

        return processedEntities.size();
    }

    private Pair<Integer, Integer> expireChargesWithGateway(List<ChargeEntity> gatewayAuthorizedCharges) {

        AtomicInteger expireCancelled = new AtomicInteger();
        AtomicInteger expireCancelFailed = new AtomicInteger();

        gatewayAuthorizedCharges.forEach(chargeEntity -> {

            ChargeEntity expiredCharge = expireChargeWithGatewayCleanup(chargeEntity);

            if (EXPIRED.getValue().equals(expiredCharge.getStatus())) {
                expireCancelled.getAndIncrement();
            } else if (EXPIRE_CANCEL_FAILED.getValue().equals(expiredCharge.getStatus())) {
                expireCancelFailed.getAndIncrement();
            }
        });

        return Pair.of(
                expireCancelled.intValue(),
                expireCancelFailed.intValue()
        );
    }

    private Pair<Integer, Integer> expireChargesOrPotentiallyForceTransitionState(List<ChargeEntity> charges) {
        AtomicInteger expireCancelled = new AtomicInteger();
        AtomicInteger expireCancelFailed = new AtomicInteger();

        charges.forEach(chargeEntity -> {
            Optional<ChargeStatus> gatewayStatus = queryService.getMappedGatewayStatus(chargeEntity);
            gatewayStatus.ifPresentOrElse(status ->
                    {
                        if (status.toExternal().isFinished()) {
                            logger.info(format("Expiring charge skipped as charge is in a terminal state on the gateway " +
                                            "provider. Attempting to update charge state to [%s]", status.getValue()),
                                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()),
                                    kv(GATEWAY_ACCOUNT_ID, chargeEntity.getGatewayAccount().getId()),
                                    kv(PROVIDER, chargeEntity.getPaymentProvider()));

                            // first try to transition to the terminal state gracefully if allowed, otherwise force the
                            // transition
                            try {
                                chargeService.transitionChargeState(chargeEntity.getExternalId(), status);
                                expireCancelled.getAndIncrement();
                            } catch (InvalidStateTransitionException e) {
                                if (forceTransitionChargeState(chargeEntity, status)) {
                                    expireCancelled.getAndIncrement();
                                } else {
                                    expireCancelFailed.getAndIncrement();
                                }
                            }
                        } else {
                            ChargeEntity expiredCharge = expireChargeWithGatewayCleanup(chargeEntity);

                            if (EXPIRED.getValue().equals(expiredCharge.getStatus())) {
                                expireCancelled.getAndIncrement();
                            } else if (EXPIRE_CANCEL_FAILED.getValue().equals(expiredCharge.getStatus())) {
                                expireCancelFailed.getAndIncrement();
                            }
                        }
                    },
                    () -> {
                        logger.info(format("Gateway status does not map to any charge " +
                                        "status in %s, expiring without cancelling on the gateway.",
                                ChargeStatus.class.getCanonicalName()),
                                kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
                        chargeService.transitionChargeState(chargeEntity.getExternalId(), EXPIRED);
                        expireCancelled.getAndIncrement();
                    });
        });

        return Pair.of(
                expireCancelled.intValue(),
                expireCancelFailed.intValue()
        );
    }
    
    private boolean forceTransitionChargeState(ChargeEntity chargeEntity, ChargeStatus status) {
        try {
            chargeService.forceTransitionChargeState(chargeEntity.getExternalId(), status);
            return true;
        } catch (InvalidForceStateTransitionException e) {
            logger.error(format("Cannot expire charge as it is in a terminal state of [%s] with " +
                            "the gateway provider and it is not possible to transition the charge " +
                            "into this state. Current state: [%s]",
                    status.getValue(), chargeEntity.getStatus()),
                    kv(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId()));
            return false;
        }
    }

    private ChargeEntity expireChargeWithGatewayCleanup(ChargeEntity chargeEntity) {
        ChargeEntity processedEntity = prepareForTermination(chargeEntity.getExternalId());
        ChargeStatus newStatus;

        try {
            GatewayResponse<BaseCancelResponse> gatewayResponse = doGatewayCancel(processedEntity);
            newStatus = determineTerminalState(gatewayResponse);
        } catch (GatewayException e) {
            newStatus = EXPIRE_FLOW.getFailureTerminalState();
            logger.error("Gateway error while cancelling the Charge - charge_external_id={}, gateway_error={}",
                    chargeEntity.getExternalId(), e.getMessage());
        }

        return chargeService.transitionChargeState(processedEntity.getExternalId(), newStatus);
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

    public Boolean forceCancelWithGateway(ChargeEntity charge) {
        try {
            GatewayResponse<BaseCancelResponse> cancelResponse = doGatewayCancel(charge);
            return handleCancelResponse(charge.getExternalId(), cancelResponse);
        } catch (GatewayException e) {
            throw new WebApplicationException(String.format(
                    "Unable to cancel charge %s with gateway: %s",
                    charge.getExternalId(),
                    e.getMessage()));
        }
    }

    private Boolean handleCancelResponse(String chargeExternalId, GatewayResponse<BaseCancelResponse> cancelResponse) {
        return cancelResponse.getBaseResponse()
                .map(r -> {
                    if (BaseCancelResponse.CancelStatus.ERROR.equals(r.cancelStatus())) {
                        logger.info("Could not cancel charge {}. Gateway returned error", chargeExternalId);
                        return false;
                    } else {
                        return true;
                    }
                })
                .orElseGet(() -> {
                    cancelResponse.getGatewayError().ifPresent(
                            e -> logger.info("Could not cancel charge {}. Gateway error {}", chargeExternalId, e.getMessage()));
                    return false;
                });
    }

    private Instant getExpiryDateForRegularCharges() {
        Duration chargeExpiryWindowSeconds = chargeSweepConfig.getDefaultChargeExpiryThreshold();
        logger.debug("Charge expiry window size in seconds: [{}]", chargeExpiryWindowSeconds.getSeconds());
        return clock.instant().minus(chargeExpiryWindowSeconds);
    }

    private Instant getDateToExpireChargesUpdatedBefore() {
        Duration chargeUpdatedWindowSeconds = chargeSweepConfig.getSkipExpiringChargesLastUpdatedInSeconds();
        logger.debug("Charge updated window size in seconds: [{}]", chargeUpdatedWindowSeconds.getSeconds());
        return clock.instant().minus(chargeUpdatedWindowSeconds);
    }

    private Instant getExpiryDateForAwaitingCaptureRequest() {
        Duration chargeExpiryWindowSeconds = chargeSweepConfig.getAwaitingCaptureExpiryThreshold();
        logger.debug("Charge expiry window size for awaiting_delay_capture in seconds: [{}]", chargeExpiryWindowSeconds.getSeconds());
        return clock.instant().minus(chargeExpiryWindowSeconds);
    }
    
    private Instant getExpiryThresholdForTokens() {
        Duration tokenExpiryWindowSeconds = chargeSweepConfig.getTokenExpiryThresholdInSeconds();
        logger.debug("Token expiry window size in seconds: [{}]", tokenExpiryWindowSeconds.getSeconds());
        return clock.instant().minus(tokenExpiryWindowSeconds);
    }

    private static String getLegalStatusNames(List<ChargeStatus> legalStatuses) {
        return legalStatuses.stream().map(ChargeStatus::toString).collect(Collectors.joining(", "));
    }

    private GatewayResponse<BaseCancelResponse> doGatewayCancel(ChargeEntity chargeEntity) throws GatewayException {
        return providers.byName(chargeEntity.getPaymentGatewayName()).cancel(CancelGatewayRequest.valueOf(chargeEntity));
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

                logger.warn("Charge is not in one of the legal states. charge_external_id={}, status={}, legal_states={}",
                        chargeId, chargeEntity.getStatus(), getLegalStatusNames(EXPIRE_FLOW.getTerminatableStatuses()));

                throw new IllegalStateRuntimeException(chargeId);
            }
            chargeService.transitionChargeState(chargeEntity, newStatus);

            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();

            // Used by Sumo Logic saved search
            logger.info("Card cancel request sent - charge_external_id={}, charge_status={}, account_id={}, transaction_id={}, amount={}, operation_type={}, provider={}, provider_type={}, locking_status={}",
                    chargeEntity.getExternalId(),
                    chargeStatus,
                    gatewayAccount.getId(),
                    chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getAmount(),
                    OperationType.CANCELLATION.getValue(),
                    chargeEntity.getPaymentProvider(),
                    gatewayAccount.getType(),
                    newStatus);


            return chargeEntity;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }
}
