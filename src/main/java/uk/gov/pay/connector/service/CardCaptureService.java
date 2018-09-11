package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.ConflictRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions.isValidTransition;

public class CardCaptureService extends CardService implements TransactionalGatewayOperation<BaseCaptureResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(CardCaptureService.class);
    private static List<ChargeStatus> legalStatuses = ImmutableList.of(
            AUTHORISATION_SUCCESS,
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY
    );
    private static final Set<ChargeStatus> IGNORABLE_CAPTURE_STATES = new HashSet<>(Arrays.asList(
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY,
            CAPTURE_READY,
            CAPTURE_SUBMITTED,
            CAPTURED
    ));

    private final UserNotificationService userNotificationService;


    @Inject
    public CardCaptureService(ChargeDao chargeDao, ChargeEventDao chargeEventDao, PaymentProviders providers, UserNotificationService userNotificationService, Environment environment) {
        super(chargeDao, chargeEventDao, providers, environment);
        this.userNotificationService = userNotificationService;
    }

    public GatewayResponse<BaseCaptureResponse> doCapture(String externalId) {
        ChargeEntity charge;
        try {
            charge = preOperation(externalId);
        } catch (OptimisticLockException e) {
            LOG.info("OptimisticLockException in doCapture for charge external_id={}", externalId);
            throw new ConflictRuntimeException(externalId);
        }
        GatewayResponse<BaseCaptureResponse> operationResponse = operation(charge);
        return postOperation(externalId, operationResponse);
    }

    @Transactional
    @Override
    public ChargeEntity preOperation(String chargeId) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> preOperation(chargeEntity, CardService.OperationType.CAPTURE, legalStatuses, CAPTURE_READY))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    @Transactional
    public ChargeEntity markChargeAsEligibleForCapture(String externalId) {
        return chargeDao.findByExternalId(externalId).map(charge -> {

            ChargeStatus targetStatus = charge.isDelayedCapture() ? AWAITING_CAPTURE_REQUEST : CAPTURE_APPROVED;

            ChargeStatus currentChargeStatus = fromString(charge.getStatus());
            if (!isValidTransition(currentChargeStatus, targetStatus)) {
                LOG.error("Charge with state " + currentChargeStatus + " cannot proceed to " + targetStatus +
                        " [charge_external_id={}, charge_status={}]", charge.getExternalId(), currentChargeStatus);
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            LOG.info("{} for charge [charge_external_id={}]", targetStatus, externalId);
            charge.setStatus(targetStatus);
            chargeEventDao.persistChargeEventOf(charge, Optional.empty());
            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    @Transactional
    public void markChargeAsCaptureError(String chargeId) {
        LOG.error("CAPTURE_ERROR for charge [charge_external_id={}] - reached maximum number of capture attempts",
                chargeId);
        chargeDao.findByExternalId(chargeId).ifPresent(chargeEntity -> {
            chargeEntity.setStatus(CAPTURE_ERROR);
            chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
        });
    }

    @Transactional
    public ChargeEntity markChargeAsCaptureApproved(String externalId) {
        return chargeDao.findByExternalId(externalId).map(charge -> {

            ChargeStatus currentStatus = fromString(charge.getStatus());

            if (IGNORABLE_CAPTURE_STATES.contains(currentStatus)) {
                LOG.info("Skipping charge [charge_external_id={}] with status [{}] from marking as CAPTURE APPROVED", currentStatus, externalId);
                return charge;
            }

            ChargeStatus targetStatus = CAPTURE_APPROVED;

            if (!isValidTransition(currentStatus, targetStatus)) {
                LOG.error("Charge with state {} cannot proceed to {} [charge_external_id={}, charge_status={}]",
                        currentStatus, targetStatus, charge.getExternalId(), currentStatus);
                throw new ConflictRuntimeException(charge.getExternalId(),
                        format("attempt to perform delayed capture on charge not in %s state.", AWAITING_CAPTURE_REQUEST));
            }

            LOG.info("{} for charge [charge_external_id={}]", targetStatus, externalId);
            charge.setStatus(targetStatus);
            chargeEventDao.persistChargeEventOf(charge, Optional.empty());
            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }

    @Override
    public GatewayResponse<BaseCaptureResponse> operation(ChargeEntity chargeEntity) {
        return getPaymentProviderFor(chargeEntity)
                .capture(CaptureGatewayRequest.valueOf(chargeEntity));
    }

    @Transactional
    @Override
    public GatewayResponse<BaseCaptureResponse> postOperation(String chargeId, GatewayResponse<BaseCaptureResponse> operationResponse) {
        return chargeDao.findByExternalId(chargeId)
                .map(chargeEntity -> {

                    ChargeStatus nextStatus = determineNextStatus(operationResponse);

                    String transactionId = operationResponse.getBaseResponse()
                            .map(BaseCaptureResponse::getTransactionId).orElse("");

                    LOG.info("Capture for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                            chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), chargeEntity.getGatewayTransactionId(),
                            chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                            operationResponse, chargeEntity.getStatus(), nextStatus);

                    chargeEntity.setStatus(nextStatus);

                    if (isBlank(transactionId)) {
                        LOG.warn("Card capture response received with no transaction id. - charge_external_id={}", chargeId);
                    }

                    GatewayAccountEntity account = chargeEntity.getGatewayAccount();

                    metricRegistry.counter(format("gateway-operations.%s.%s.%s.capture.result.%s", account.getGatewayName(), account.getType(), account.getId(), nextStatus.toString())).inc();

                    chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());

                    if (operationResponse.isSuccessful()) {
                        userNotificationService.notifyPaymentSuccessEmail(chargeEntity);
                    }

                    //for sandbox, immediately move from CAPTURE_SUBMITTED to CAPTURED, as there will be no external notification
                    if (chargeEntity.getPaymentGatewayName() == PaymentGatewayName.SANDBOX) {
                        chargeEntity.setStatus(CAPTURED);
                        ZonedDateTime gatewayEventTime = ZonedDateTime.now();
                        chargeEventDao.persistChargeEventOf(chargeEntity, Optional.of(gatewayEventTime));
                    }

                    return operationResponse;
                })
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private ChargeStatus determineNextStatus(GatewayResponse<BaseCaptureResponse> operationResponse) {
        if (operationResponse.isSuccessful()) {
            return CAPTURE_SUBMITTED;
        } else {
            return operationResponse.getGatewayError()
                    .map(timeoutError -> CAPTURE_APPROVED_RETRY)
                    .orElse(CAPTURE_ERROR);
        }
    }
}
