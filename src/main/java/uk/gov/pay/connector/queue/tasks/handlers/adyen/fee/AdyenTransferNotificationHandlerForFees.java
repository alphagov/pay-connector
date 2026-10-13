package uk.gov.pay.connector.queue.tasks.handlers.adyen.fee;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.FeeIncurredEvent;
import uk.gov.pay.connector.events.model.refund.RefundFeeIncurredEvent;
import uk.gov.pay.connector.fee.dao.FeeDao;
import uk.gov.pay.connector.fee.dao.FeeProcessingLockDao;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.adyen.webhook.json.transfer.AdyenTransferData;
import uk.gov.pay.connector.gateway.adyen.webhook.json.transfer.AdyenTransferNotification;
import uk.gov.pay.connector.refund.exception.RefundNotFoundRuntimeException;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import java.time.InstantSource;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.FeeType.GATEWAY;
import static uk.gov.pay.connector.charge.util.AdyenFeeCalculator.generateFeeList;
import static uk.gov.pay.connector.charge.util.AdyenFeeCalculator.hasAllExpectedFeeRecordsForAdyen;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.LIVE;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenFeeTransferClassifier.classify;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenFeeTransferType.UNSUPPORTED;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenTransactionTypeForTransfer.from;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenTransferNotificationValidator.validate;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class AdyenTransferNotificationHandlerForFees {

    public static final String PAYMENT_FEE = "PaymentFee";
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final ChargeService chargeService;
    private final RefundService refundService;
    private final EventService eventService;
    private final FeeDao feeDao;
    private final FeeProcessingLockDao feeProcessingLockDao;
    private final InstantSource instantSource;
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenTransferNotificationHandlerForFees.class);

    private static final String LOGGING_KEY_PLATFORM_PAYMENT_TYPE = "platform_payment_type";

    @Inject
    public AdyenTransferNotificationHandlerForFees(AdyenGatewayConfig adyenGatewayConfig,
                                                   ChargeService chargeService,
                                                   RefundService refundService,
                                                   EventService eventService,
                                                   FeeDao feeDao,
                                                   FeeProcessingLockDao feeProcessingLockDao,
                                                   InstantSource instantSource) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.chargeService = chargeService;
        this.refundService = refundService;
        this.eventService = eventService;
        this.feeDao = feeDao;
        this.feeProcessingLockDao = feeProcessingLockDao;
        this.instantSource = instantSource;
    }

    @Transactional
    public void process(AdyenTransferNotification transferNotification) {
        var adyenTransferData = transferNotification.data();

        try {
            validate(adyenTransferData);

            MDC.put(LOGGING_KEY_PLATFORM_PAYMENT_TYPE, adyenTransferData.categoryData().platformPaymentType());

            from(adyenTransferData.type(), adyenTransferData.status())
                    .ifPresentOrElse(transactionTypeForTransfer -> {
                                switch (transactionTypeForTransfer) {
                                    case CAPTURE:
                                        processPaymentTransferEvent(adyenTransferData);
                                        break;
                                    case REFUND:
                                        processRefundTransferEvent(adyenTransferData, transferNotification.environment());
                                        break;
                                }
                            },
                            () -> LOGGER.atInfo()
                                    .setMessage("Ignored transfer notification for unknown type or status")
                                    .log());
        } finally {
            List.of(LOGGING_KEY_PLATFORM_PAYMENT_TYPE,
                            PAYMENT_EXTERNAL_ID,
                            REFUND_EXTERNAL_ID)
                    .forEach(MDC::remove);
        }
    }

    private void processPaymentTransferEvent(AdyenTransferData adyenTransferData) {
        AdyenFeeTransferType adyenFeeTransferType = classify(adyenTransferData.categoryData().platformPaymentType(),
                adyenTransferData.description());

        if (adyenFeeTransferType == UNSUPPORTED) {
            LOGGER.atInfo()
                    .setMessage("Skipping transfer notification for fees as it is not one of Commission, Interchange or SchemeFee payment type")
                    .log();
            return;
        }

        String chargeExternalId = adyenTransferData.categoryData().paymentMerchantReference();
        MDC.put(PAYMENT_EXTERNAL_ID, chargeExternalId);

        ChargeEntity chargeEntity = chargeService.findChargeByExternalId(chargeExternalId);

        List<Fee> fees = generateFeeList(adyenFeeTransferType,
                adyenTransferData.amount().value(),
                adyenGatewayConfig.getPaymentOrRefundGatewayFeeInPence(),
                adyenGatewayConfig.getFraudAvoidanceFeeInPence()
        );

        insertPaymentFeesAndEmitFeeIncurredEvent(chargeEntity, fees);

        LOGGER.atInfo()
                .setMessage("Processed transfer notification for fees")
                .log();
    }

    private void processRefundTransferEvent(AdyenTransferData adyenTransferData, String environment) {

        if (!adyenTransferData.categoryData().platformPaymentType().equals(PAYMENT_FEE)) {
            LOGGER.atInfo()
                    .setMessage("Skipping transfer notification for refund fees as it is not PaymentFee payment type")
                    .log();
            return;
        }

        var refundExternalId = adyenTransferData.categoryData().modificationMerchantReference();
        Optional<RefundEntity> mayBeRefund = refundService.findRefundByExternalId(refundExternalId);

        if (mayBeRefund.isEmpty()) {
            throw new RefundNotFoundRuntimeException("Refund not found for Adyen transfer webhook notification");
        }

        RefundEntity refundEntity = mayBeRefund.get();
        MDC.put(REFUND_EXTERNAL_ID, refundExternalId);

        var feeAmount = adyenTransferData.amount().value();
        var refundFee = Fee.of(GATEWAY, feeAmount, null);
        FeeEntity feeEntity = new FeeEntity(refundEntity, instantSource.instant(), refundFee);

        var feeInserted = feeDao.insertRefundFeeIfAbsent(feeEntity, refundEntity.getId());

        if (feeInserted) {
            mayBeRefund = refundService.findRefundByExternalId(refundExternalId);

            if (mayBeRefund.isPresent()) {
                refundEntity = mayBeRefund.get();
                emitFeeEventForRefund(refundEntity);

                checkRefundFeeEqualsConfigAndLog(environment, feeAmount);

                LOGGER.atInfo()
                        .setMessage("Processed transfer notification for refund fees")
                        .log();
            }
        } else {
            LOGGER.atInfo()
                    .setMessage("Refund fee already exists")
                    .addKeyValue(REFUND_EXTERNAL_ID, refundEntity.getExternalId())
                    .log();
        }
    }

    private void checkRefundFeeEqualsConfigAndLog(String environment, Long feeAmount) {
        if (adyenGatewayConfig.getPaymentOrRefundGatewayFeeInPence() != feeAmount) {
            var loggingMessage = "Refund fee does not match Adyen gateway config fee value";
            Level logLevel = LIVE.name().equalsIgnoreCase(environment) ? Level.ERROR : Level.WARN;

            LOGGER.atLevel(logLevel)
                    .setMessage(loggingMessage)
                    .addKeyValue("environment", environment)
                    .log();
        }
    }

    private void insertPaymentFeesAndEmitFeeIncurredEvent(ChargeEntity chargeEntity, List<Fee> feesToInsert) {
        feeProcessingLockDao.insertIfAbsent(chargeEntity.getId());
        feeProcessingLockDao.lockForProcessing(chargeEntity.getId());

        boolean feeRecordInserted = false;
        for (Fee fee : feesToInsert) {
            FeeEntity feeEntity = new FeeEntity(chargeEntity, instantSource.instant(), fee);
            boolean inserted = feeDao.insertChargeFeeIfAbsent(feeEntity, chargeEntity.getId());
            feeRecordInserted = inserted || feeRecordInserted;
        }

        if (feeRecordInserted) {
            chargeEntity = chargeService.findChargeByExternalId(chargeEntity.getExternalId());
            List<Fee> feeList = chargeEntity.getFees()
                    .stream()
                    .map(feeEntity -> new Fee(feeEntity.getFeeType(), feeEntity.getAmountCollected(), feeEntity.getFeeSubType()))
                    .toList();

            if (hasAllExpectedFeeRecordsForAdyen(feeList)) {
                emitFeeEventForPayment(chargeEntity);
            }
        } else {
            LOGGER.atInfo()
                    .setMessage("No fee records inserted")
                    .log();
        }
    }

    private void emitFeeEventForPayment(ChargeEntity chargeEntity) {
        try {
            FeeIncurredEvent event = FeeIncurredEvent.from(chargeEntity);
            emitEvent(event);
        } catch (EventCreationException e) {
            LOGGER.atWarn()
                    .setMessage("Error creating FEE_INCURRED event from charge entity")
                    .log();
            throw new RuntimeException(e);
        }
    }

    private void emitFeeEventForRefund(RefundEntity refundEntity) {
        try {
            emitEvent(RefundFeeIncurredEvent.from(refundEntity));
        } catch (EventCreationException e) {
            LOGGER.atWarn()
                    .setMessage("Error creating REFUND_FEE_INCURRED event")
                    .setCause(e)
                    .log();
            throw new RuntimeException(e);
        }
    }

    private void emitEvent(Event event) {
        try {
            eventService.emitEvent(event, false);
            LOGGER.atInfo()
                    .setMessage("Fee incurred event sent to event queue")
                    .addKeyValue(LEDGER_EVENT_TYPE, event.getEventType())
                    .log();
        } catch (QueueException e) {
            LOGGER.atWarn()
                    .setMessage("Error emitting FEE_INCURRED event")
                    .setCause(e)
                    .log();
            throw new RuntimeException(e);
        }
    }

}
