package uk.gov.pay.connector.paymentprocessor.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions.isValidTransition;

public class CardCaptureService extends CardService implements TransactionalGatewayOperation<BaseCaptureResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(CardCaptureService.class);
    private static final List<ChargeStatus> LEGAL_STATUSES = ImmutableList.of(
            AUTHORISATION_SUCCESS,
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY
    );
    private static final List<ChargeStatus> IGNORABLE_CAPTURE_STATES = ImmutableList.of(
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY,
            CAPTURE_READY,
            CAPTURE_SUBMITTED,
            CAPTURED
    );

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
                .map(chargeEntity -> lockChargeForProcessing(chargeEntity, CardService.OperationType.CAPTURE, LEGAL_STATUSES, CAPTURE_READY))
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

            return changeChargeStatus(charge, targetStatus);
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

            if (charge.hasStatus(IGNORABLE_CAPTURE_STATES)) {
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

            return changeChargeStatus(charge, targetStatus);
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
                        userNotificationService.sendPaymentConfirmedEmail(chargeEntity);
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

    private ChargeEntity changeChargeStatus(ChargeEntity charge, ChargeStatus targetStatus) {
        LOG.info("{} for charge [charge_external_id={}]", targetStatus, charge.getExternalId());
        charge.setStatus(targetStatus);
        chargeEventDao.persistChargeEventOf(charge, Optional.empty());
        return charge;
    }
}
