package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions.isValidTransition;

public class CardCaptureService {

    private static final Logger LOG = LoggerFactory.getLogger(CardCaptureService.class);
    private static final List<ChargeStatus> IGNORABLE_CAPTURE_STATES = ImmutableList.of(
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY,
            CAPTURE_READY,
            CAPTURE_SUBMITTED,
            CAPTURED
    );

    private final UserNotificationService userNotificationService;
    private final ChargeService chargeService;
    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final PaymentProviders providers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected MetricRegistry metricRegistry;

    @Inject
    public CardCaptureService(ChargeService chargeService, ChargeDao chargeDao, ChargeEventDao chargeEventDao,
                              PaymentProviders providers, UserNotificationService userNotificationService,
                              Environment environment) {
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.providers = providers;
        this.metricRegistry = environment.metrics();
        this.userNotificationService = userNotificationService;
    }

    public GatewayResponse<BaseCaptureResponse> doCapture(String externalId) {
        ChargeEntity charge;
        try {
            charge = prepareChargeForCapture(externalId);
        } catch (OptimisticLockException e) {
            LOG.info("OptimisticLockException in doCapture for charge external_id={}", externalId);
            throw new ConflictRuntimeException(externalId);
        }
        GatewayResponse<BaseCaptureResponse> operationResponse = capture(charge);
        processGatewayCaptureResponse(externalId, charge.getStatus(), operationResponse);
        return operationResponse;
    }

    @Transactional
    public ChargeEntity prepareChargeForCapture(String chargeId) {
        return chargeService.lockChargeForProcessing(chargeId, OperationType.CAPTURE);
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
    void markChargeAsCaptureError(String chargeId) {
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

    public GatewayResponse<BaseCaptureResponse> capture(ChargeEntity chargeEntity) {
        return getPaymentProviderFor(chargeEntity)
                .getCaptureHandler()
                .capture(CaptureGatewayRequest.valueOf(chargeEntity));
    }

    @Transactional
    public void processGatewayCaptureResponse(String chargeId, String oldStatus, GatewayResponse<BaseCaptureResponse> operationResponse) {

        ChargeStatus nextStatus = determineNextStatus(operationResponse);
        checkTransactionId(chargeId, operationResponse);

        ChargeEntity charge = chargeService.updateChargePostCapture(chargeId, nextStatus);

        LOG.info("Capture for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                charge.getExternalId(), charge.getPaymentGatewayName().getName(), charge.getGatewayTransactionId(),
                charge.getGatewayAccount().getAnalyticsId(), charge.getGatewayAccount().getId(),
                operationResponse, oldStatus, nextStatus);

        metricRegistry.counter(format("gateway-operations.%s.%s.%s.capture.result.%s",
                charge.getGatewayAccount().getGatewayName(),
                charge.getGatewayAccount().getType(),
                charge.getGatewayAccount().getId(), nextStatus.toString())).inc();

        if (operationResponse.isSuccessful()) {
            userNotificationService.sendPaymentConfirmedEmail(charge);
        }
    }

    private void checkTransactionId(String chargeId, GatewayResponse<BaseCaptureResponse> operationResponse) {
        String transactionId = operationResponse.getBaseResponse()
                .map(BaseCaptureResponse::getTransactionId).orElse("");
        if (isBlank(transactionId)) {
            LOG.warn("Card capture response received with no transaction id. - charge_external_id={}", chargeId);
        }
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

    PaymentProvider<BaseCaptureResponse, ?> getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }
}
