package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;

public class CardCaptureService {

    private static final Logger LOG = LoggerFactory.getLogger(CardCaptureService.class);

    private final UserNotificationService userNotificationService;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected MetricRegistry metricRegistry;

    @Inject
    public CardCaptureService(ChargeService chargeService, PaymentProviders providers,
                              UserNotificationService userNotificationService,
                              Environment environment) {
        this.chargeService = chargeService;
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

    private GatewayResponse<BaseCaptureResponse> capture(ChargeEntity chargeEntity) {
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

    PaymentProvider<?> getPaymentProviderFor(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName());
    }
}
