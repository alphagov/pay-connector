package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConflictRuntimeException;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.FeeIncurredEvent;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.paymentprocessor.model.OperationType;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;
import javax.ws.rs.WebApplicationException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class CardCaptureService {

    private static final Logger LOG = LoggerFactory.getLogger(CardCaptureService.class);

    private final UserNotificationService userNotificationService;
    private final ChargeService chargeService;
    private final PaymentProviders providers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final EventService eventService;
    private final AgreementService agreementService;
    protected MetricRegistry metricRegistry;
    protected Clock clock;
    protected CaptureQueue captureQueue;

    @Inject
    public CardCaptureService(ChargeService chargeService,
                              PaymentProviders providers,
                              UserNotificationService userNotificationService,
                              Environment environment,
                              Clock clock,
                              CaptureQueue captureQueue,
                              EventService eventService,
                              AgreementService agreementService) {
        this.chargeService = chargeService;
        this.providers = providers;
        this.metricRegistry = environment.metrics();
        this.clock = clock;
        this.userNotificationService = userNotificationService;
        this.captureQueue = captureQueue;
        this.eventService = eventService;
        this.agreementService = agreementService;
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
    
    
//    This probably should be transactional, for some reason an optimistic lock conflict when actually capturing 
    // this must be used by both the confirm route and the subsequent capture process, not sure how
//    @Transactional
    public ChargeEntity markChargeAsEligibleForCapture(String externalId) {
        ChargeEntity charge = chargeService.markChargeAsEligibleForCapture(externalId);
        
        if (charge.isSavePaymentInstrumentToAgreement()) {
            // @TODO(sfount): consider making charge approved and payment instrument confirmed transactional
            // TODO(sfount): consider what happens with the event going out with this
            agreementService.find(charge.getAgreementId())
                    .ifPresent(agreementEntity -> {
                        // TODO(sfount): consider what happens with the event going out with this
                        agreementEntity.setPaymentInstrument(charge.getPaymentInstrument());
                    });
            charge.getPaymentInstrument().setPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE);
        }
        

        if (!charge.isDelayedCapture()) {
            addChargeToCaptureQueue(charge);
            userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        }

        return charge;
    }

    @Transactional
    void markChargeAsCaptureError(String chargeId) {
        LOG.error("CAPTURE_ERROR for charge [charge_external_id={}] - reached maximum number of capture attempts",
                chargeId); // log line used by Splunk for alerting
        chargeService.transitionChargeState(chargeId, CAPTURE_ERROR);
    }

    public ChargeEntity markDelayedCaptureChargeAsCaptureApproved(String externalId) {
        ChargeEntity charge = chargeService.markDelayedCaptureChargeAsCaptureApproved(externalId);
        addChargeToCaptureQueue(charge);
        return charge;
    }

    private CaptureResponse capture(ChargeEntity chargeEntity) {
        return providers.byName(chargeEntity.getPaymentGatewayName())
                .capture(CaptureGatewayRequest.valueOf(chargeEntity));
    }

    @Transactional
    public void processGatewayCaptureResponse(String chargeId, String oldStatus, CaptureResponse captureResponse) {
        ChargeStatus nextStatus = determineNextStatus(captureResponse);
        checkTransactionId(chargeId, captureResponse);

        ChargeEntity charge = chargeService.findChargeByExternalId(chargeId);

        List<Fee> feeList = captureResponse.getFeeList();
        feeList.stream().map(fee -> new FeeEntity(charge, clock.instant(), fee)).forEach(charge::addFee);
        
        // We only want to emit the FEE_INCURRED event for charges using the new Stripe pricing. The original Stripe 
        // pricing only has a single fee, whereas the v2 pricing will have at least 2 fees (transaction, radar) 
        // applied to each charge. This size check can be removed after we have switched to the new pricing.
        if (feeList.size() > 1) {
            try {
                sendToEventQueue(FeeIncurredEvent.from(charge));
            } catch (EventCreationException e) {
                LOG.warn(format("Failed to create fee incurred event [%s], exception: [%s]", charge.getExternalId(), e.getMessage()));
            }
        }

        chargeService.updateChargePostCapture(charge, nextStatus);

        // Used by Sumo Logic saved search
        LOG.info("Capture for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                charge.getExternalId(), charge.getPaymentGatewayName().getName(), charge.getGatewayTransactionId(),
                charge.getGatewayAccount().getAnalyticsId(), charge.getGatewayAccount().getId(),
                captureResponse, oldStatus, nextStatus);

        metricRegistry.counter(format("gateway-operations.%s.%s.capture.result.%s",
                charge.getPaymentProvider(),
                charge.getGatewayAccount().getType(),
                nextStatus.toString())).inc();

        if (captureResponse.isSuccessful() && charge.isDelayedCapture()) {
            userNotificationService.sendPaymentConfirmedEmail(charge, charge.getGatewayAccount());
        }
    }

    private void addChargeToCaptureQueue(ChargeEntity charge) {
        try {
            captureQueue.sendForCapture(charge);
        } catch (QueueException e) {
            logger.error("Exception sending charge [{}] to capture queue", charge.getExternalId());
            throw new WebApplicationException(format(
                    "Unable to schedule charge [%s] for capture - %s",
                    charge.getExternalId(), e.getMessage()));
        }
    }

    private void checkTransactionId(String chargeId, CaptureResponse operationResponse) {
        Optional<String> transactionId = operationResponse.getTransactionId();
        if (transactionId.isEmpty()) {
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

    private void sendToEventQueue(Event event) {
        eventService.emitAndRecordEvent(event);
        logger.info("Fee incurred event sent to event queue",
                kv(LEDGER_EVENT_TYPE, event.getEventType()),
                kv(PAYMENT_EXTERNAL_ID, event.getResourceExternalId()));
    }
}
