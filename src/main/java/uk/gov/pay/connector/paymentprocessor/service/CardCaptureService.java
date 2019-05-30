package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.fee.dao.FeeDao;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import javax.ws.rs.WebApplicationException;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;

public class CardCaptureService {

    private static final Logger LOG = LoggerFactory.getLogger(CardCaptureService.class);

    private final UserNotificationService userNotificationService;
    private final FeeDao feeDao;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected MetricRegistry metricRegistry;
    protected CaptureQueue captureQueue;
    protected Boolean captureUsingSQS;

    @Inject
    public CardCaptureService(ChargeService chargeService,
                              FeeDao feeDao,
                              PaymentProviders providers,
                              UserNotificationService userNotificationService,
                              Environment environment,
                              CaptureQueue captureQueue,
                              ConnectorConfiguration connectorConfiguration) {
        this.chargeService = chargeService;
        this.feeDao = feeDao;
        this.providers = providers;
        this.metricRegistry = environment.metrics();
        this.userNotificationService = userNotificationService;
        this.captureQueue = captureQueue;
        this.captureUsingSQS = connectorConfiguration.getCaptureProcessConfig().getCaptureUsingSQS();
    }

    public CaptureResponse doCapture(String externalId) {
        ChargeEntity charge;
        try {
            charge = prepareChargeForCapture(externalId);
        } catch (OptimisticLockException e) {
            LOG.info("OptimisticLockException in doCapture for charge external_id={}", externalId);
            throw new ConflictRuntimeException(externalId);
        }
        CaptureResponse operationResponse = capture(charge);
        processGatewayCaptureResponse(externalId, charge.getStatus(), operationResponse);
        
        return operationResponse;
    }

    @Transactional
    public ChargeEntity prepareChargeForCapture(String chargeId) {
        return chargeService.lockChargeForProcessing(chargeId, OperationType.CAPTURE);
    }

    @Transactional
    public ChargeEntity markChargeAsEligibleForCapture(String externalId) {
        final ChargeEntity charge = chargeService.findChargeById(externalId);

        if (charge.isDelayedCapture()) {
            return chargeService.updateChargeStatus(charge, AWAITING_CAPTURE_REQUEST);

        } else {
            addChargeToCaptureQueue(charge);
            try {
                return chargeService.updateChargeStatus(charge, CAPTURE_APPROVED);
            } catch (InvalidStateTransitionException e) {
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }
        }
    }

    @Transactional
    void markChargeAsCaptureError(String chargeId) {
        LOG.error("CAPTURE_ERROR for charge [charge_external_id={}] - reached maximum number of capture attempts",
                chargeId);
        chargeService.updateChargeStatus(chargeId, CAPTURE_ERROR);
    }

    @Transactional
    public ChargeEntity markChargeAsCaptureApproved(String externalId) {
        ChargeEntity charge = chargeService.findChargeById(externalId);
        addChargeToCaptureQueue(charge);
        return chargeService.markChargeAsCaptureApproved(externalId);
    }

    private CaptureResponse capture(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName())
                .capture(CaptureGatewayRequest.valueOf(chargeEntity));
    }

    @Transactional
    public void processGatewayCaptureResponse(String chargeId, String oldStatus, CaptureResponse captureResponse) {

        ChargeStatus nextStatus = determineNextStatus(captureResponse);
        checkTransactionId(chargeId, captureResponse);


        ChargeEntity charge = chargeService.updateChargePostCapture(chargeId, nextStatus);
        captureResponse.getFee().ifPresent(fee -> persistFee(charge, fee));

        // Used by Sumo Logic saved search
        LOG.info("Capture for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                charge.getExternalId(), charge.getPaymentGatewayName().getName(), charge.getGatewayTransactionId(),
                charge.getGatewayAccount().getAnalyticsId(), charge.getGatewayAccount().getId(),
                captureResponse, oldStatus, nextStatus);

        metricRegistry.counter(format("gateway-operations.%s.%s.%s.capture.result.%s",
                charge.getGatewayAccount().getGatewayName(),
                charge.getGatewayAccount().getType(),
                charge.getGatewayAccount().getId(), nextStatus.toString())).inc();

        if (captureResponse.isSuccessful()) {
            userNotificationService.sendPaymentConfirmedEmail(charge);
        }
    }

    @Transactional
    public void persistFee(ChargeEntity charge, Long feeAmount) {
        FeeEntity fee = new FeeEntity(charge, feeAmount);
        feeDao.persist(fee);
    }

    private void addChargeToCaptureQueue(ChargeEntity charge) {
        if (captureUsingSQS) {
            try {
                captureQueue.sendForCapture(charge);
            } catch (QueueException e) {
                logger.error("Exception sending charge [{}] to capture queue", charge.getExternalId());
                throw new WebApplicationException(format(
                        "Unable to schedule charge [%s] for capture - %s",
                        charge.getExternalId(), e.getMessage()));
            }
        }
    }

    private void checkTransactionId(String chargeId, CaptureResponse operationResponse) {
        Optional<String> transactionId = operationResponse.getTransactionId();
        if (!transactionId.isPresent()) {
            LOG.warn("Card capture response received with no transaction id. - charge_external_id={}", chargeId);
        }
    }

    private ChargeStatus determineNextStatus(CaptureResponse operationResponse) {
        if (operationResponse.getError().isPresent()) {
            return CAPTURE_APPROVED_RETRY;
        } else if (PENDING.equals(operationResponse.state())) {
            return CAPTURE_SUBMITTED;
        } else {
            return CAPTURED;
        }
    }
}
