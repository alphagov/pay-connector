package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.fee.dao.FeeDao;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.FeeProcessor;
import uk.gov.pay.connector.gateway.FeeProcessors;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecoupFeeRequest;
import uk.gov.pay.connector.gateway.model.response.RecoupFeeResponse;
import uk.gov.pay.connector.paymentprocessor.exception.ChargeCaptureException;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;

public class CardCaptureService {

    private static final Logger LOG = LoggerFactory.getLogger(CardCaptureService.class);

    private final UserNotificationService userNotificationService;
    private final ChargeService chargeService;
    private final FeeDao feeDao;
    private final PaymentProviders providers;
    private final FeeProcessors feeProcessors;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected MetricRegistry metricRegistry;

    @Inject
    public CardCaptureService(ChargeService chargeService,
                              FeeDao feedao,
                              PaymentProviders providers,
                              FeeProcessors feeProcessors,
                              UserNotificationService userNotificationService,
                              Environment environment) {
        this.chargeService = chargeService;
        this.feeDao = feedao;
        this.providers = providers;
        this.feeProcessors = feeProcessors;
        this.metricRegistry = environment.metrics();
        this.userNotificationService = userNotificationService;
    }

    public void doCapture(String externalId) throws ChargeCaptureException {
        ChargeEntity charge;
        try {
            charge = prepareChargeForCapture(externalId);
        } catch (OptimisticLockException e) {
            LOG.info("OptimisticLockException in doCapture for charge external_id={}", externalId);
            throw new ConflictRuntimeException(externalId);
        }
        CaptureResponse captureResponse = capture(charge);
        ChargeStatus nextStatus = determineNextStatus(captureResponse);
        checkTransactionId(charge.getExternalId(), captureResponse);




        captureResponse.getFeeAmount()
                .map(feeAmount -> new FeeEntity(charge, feeAmount))
                .ifPresent(fee -> recoupFee(charge, fee));
        // Used by Sumo Logic saved search
        LOG.info("Capture for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                charge.getExternalId(), charge.getPaymentGatewayName().getName(), charge.getGatewayTransactionId(),
                charge.getGatewayAccount().getAnalyticsId(), charge.getGatewayAccount().getId(),
                captureResponse, charge.getStatus(), nextStatus);

        metricRegistry.counter(format("gateway-operations.%s.%s.%s.capture.result.%s",
                charge.getGatewayAccount().getGatewayName(),
                charge.getGatewayAccount().getType(),
                charge.getGatewayAccount().getId(), nextStatus.toString())).inc();
        ChargeEntity updatedCharge = chargeService.updateChargePostCapture(charge.getExternalId(), nextStatus);


        userNotificationService.sendPaymentConfirmedEmail(updatedCharge);
    }

    public void recoupFee(ChargeEntity charge, FeeEntity fee) {
        FeeProcessor feeProcessor = feeProcessors.byPaymentGatewayName(charge.getPaymentGatewayName());
        feeProcessor.calculateFee(charge.getAmount(), fee.getAmountDue());
        feeDao.persist(fee);
        RecoupFeeResponse feeResponse = feeProcessor.recoupFee(RecoupFeeRequest.of(charge, fee));
        if (feeResponse.isSuccessful()) {
            fee.setAmountCollected(feeResponse.getAmountCollected());
            feeDao.merge(fee);
        } 
    }


    @Transactional
    public ChargeEntity prepareChargeForCapture(String chargeId) {
        return chargeService.lockChargeForProcessing(chargeId, OperationType.CAPTURE);
    }

    @Transactional
    public ChargeEntity markChargeAsEligibleForCapture(String externalId) {
        return chargeService.markChargeAsEligibleForCapture(externalId);
    }

    @Transactional
    void markChargeAsCaptureError(String chargeId) {
        LOG.error("CAPTURE_ERROR for charge [charge_external_id={}] - reached maximum number of capture attempts",
                chargeId);
        chargeService.updateChargeStatus(chargeId, CAPTURE_ERROR);
    }

    @Transactional
    public ChargeEntity markChargeAsCaptureApproved(String externalId) {
        return chargeService.markChargeAsCaptureApproved(externalId);
    }

    private CaptureResponse capture(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName())
                .capture(CaptureGatewayRequest.valueOf(chargeEntity));
    }

    @Transactional
    public void processGatewayCaptureResponse(String chargeId, String oldStatus, CaptureResponse operationResponse) {


    }

    private void checkTransactionId(String chargeId, CaptureResponse operationResponse) {
        Optional<String> transactionId = operationResponse.getTransactionId();
        if (!transactionId.isPresent()) {
            LOG.warn("Card capture response received with no transaction id. - charge_external_id={}", chargeId);
        }
    }

    private ChargeStatus determineNextStatus(CaptureResponse operationResponse) {
        if (operationResponse.getError().isPresent()) return CAPTURE_APPROVED_RETRY;
        if (operationResponse.state().equals(PENDING)) return CAPTURE_SUBMITTED;
        else return CAPTURED;
    }
}
