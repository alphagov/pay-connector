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
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.service.StatusFlow.EXPIRE_FLOW;

public class ChargeExpiryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String EXPIRY_SUCCESS = "expiry-success";
    private static final String EXPIRY_FAILED = "expiry-failed";

    static final List<ChargeStatus> EXPIRABLE_REGULAR_STATUSES = ImmutableList.of(
            CREATED,
            ENTERING_CARD_DETAILS,
            AUTHORISATION_3DS_READY,
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_SUCCESS);

    static final List<ChargeStatus> EXPIRABLE_AWAITING_CAPTURE_REQUEST_STATUS = ImmutableList.of(AWAITING_CAPTURE_REQUEST);

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final PaymentProviders providers;
    static final List<ChargeStatus> GATEWAY_CANCELLABLE_STATUSES = Arrays.asList(
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_SUCCESS,
            AWAITING_CAPTURE_REQUEST
    );
    private final ChargeSweepConfig chargeSweepConfig;

    @Inject
    public ChargeExpiryService(ChargeDao chargeDao,
                               ChargeEventDao chargeEventDao,
                               PaymentProviders providers,
                               ConnectorConfiguration config) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
        this.chargeSweepConfig = config.getChargeSweepConfig();
    }

    Map<String, Integer> expire(List<ChargeEntity> charges) {
        Map<Boolean, List<ChargeEntity>> chargesToProcessExpiry = charges
                .stream()
                .collect(Collectors.partitioningBy(chargeEntity ->
                        GATEWAY_CANCELLABLE_STATUSES.contains(ChargeStatus.fromString(chargeEntity.getStatus())))
                );

        int expiredSuccess = expireChargesWithCancellationNotRequired(chargesToProcessExpiry.get(Boolean.FALSE));
        Pair<Integer, Integer> expireWithCancellationResult = expireChargesWithGatewayCancellation(chargesToProcessExpiry.get(Boolean.TRUE));

        return ImmutableMap.of(EXPIRY_SUCCESS, expiredSuccess + expireWithCancellationResult.getLeft(),
                EXPIRY_FAILED, expireWithCancellationResult.getRight());
    }

    public Map<String, Integer> sweepAndExpireCharges() {
        List<ChargeEntity> chargesToExpire = new ArrayList<>();
        chargesToExpire.addAll(chargeDao.findBeforeDateWithStatusIn(getExpiryDateForRegularCharges(),
                EXPIRABLE_REGULAR_STATUSES));
        chargesToExpire.addAll(chargeDao.findBeforeDateWithStatusIn(getExpiryDateForAwaitingCaptureRequest(),
                EXPIRABLE_AWAITING_CAPTURE_REQUEST_STATUS));
        logger.info("Charges found for expiry - number_of_charges={}, since_date={}, awaiting_capture_date{}",
                chargesToExpire.size(), getExpiryDateForRegularCharges(), getExpiryDateForAwaitingCaptureRequest());
        return expire(chargesToExpire);
    }

    private int expireChargesWithCancellationNotRequired(List<ChargeEntity> nonAuthSuccessCharges) {
        List<ChargeEntity> processedEntities = nonAuthSuccessCharges
                .stream().map(chargeEntity -> changeStatusTo(chargeEntity.getExternalId(), EXPIRED))
                .collect(Collectors.toList());

        return processedEntities.size();
    }

    private Pair<Integer, Integer> expireChargesWithGatewayCancellation(List<ChargeEntity> gatewayAuthorizedCharges) {

        final List<ChargeEntity> expireCancelled = newArrayList();
        final List<ChargeEntity> expireCancelFailed = newArrayList();

        gatewayAuthorizedCharges.forEach(chargeEntity -> {
            ChargeEntity processedEntity = prepareForTermination(chargeEntity.getExternalId());
            GatewayResponse<BaseCancelResponse> gatewayResponse = doGatewayCancel(processedEntity);
            ChargeEntity expiredCharge = finishExpireCancel(processedEntity.getExternalId(), gatewayResponse);

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

    private ChargeStatus determineTerminalState(ChargeEntity chargeEntity, GatewayResponse<BaseCancelResponse> cancelResponse) {
        if (!cancelResponse.isSuccessful()) {
            logUnsuccessfulResponseReasons(chargeEntity, cancelResponse);
        }

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

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public ChargeEntity finishExpireCancel(String chargeExternalId, GatewayResponse<BaseCancelResponse> gatewayResponse) {
        return chargeDao.findByExternalId(chargeExternalId).map(chargeEntity -> {
            ChargeStatus status = determineTerminalState(chargeEntity, gatewayResponse);
            logger.info("Charge status to update - charge_external_id={}, status={}, to_status={}",
                    chargeEntity.getExternalId(), chargeEntity.getStatus(), status);
            chargeEntity.setStatus(status);
            chargeEventDao.persistChargeEventOf(chargeEntity);
            return chargeEntity;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    private void logUnsuccessfulResponseReasons(ChargeEntity chargeEntity, GatewayResponse<BaseCancelResponse> gatewayResponse) {
        gatewayResponse.getGatewayError().ifPresent(error ->
                logger.error("Gateway error while cancelling the Charge - charge_external_id={}, gateway_error={}",
                        chargeEntity.getExternalId(), error));
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

    private GatewayResponse<BaseCancelResponse> doGatewayCancel(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName())
                .cancel(CancelGatewayRequest.valueOf(chargeEntity));
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
