package uk.gov.pay.connector.payout;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentIncludedInPayout;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.adyen.AdyenPaymentProvider;
import uk.gov.pay.connector.gateway.adyen.report.AdyenBalancePayoutReportRecord;
import uk.gov.pay.connector.gateway.adyen.report.AdyenPayoutReportParser;
import uk.gov.pay.connector.queue.payout.AdyenPayoutReconciliationPayload;
import uk.gov.pay.connector.queue.payout.PayoutReconcileHandler;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class AdyenPayoutReconciliationHandler implements PayoutReconcileHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenPayoutReconciliationHandler.class);
    public static final String BALANCE_ACCOUNT_ID = "balance_account_id";
    public static final String CATEGORY = "category";
    public static final String TRANSACTION_ID = "transaction_id";

    private final AdyenPayoutReportParser adyenPayoutReportParser;
    private final AdyenPaymentProvider adyenPaymentProvider;
    private final EventService eventService;
    private final PayoutEmitterService payoutEmitterService;
    

    @Inject
    public AdyenPayoutReconciliationHandler(PaymentProviders paymentProviders,
                                            AdyenPayoutReportParser adyenPayoutReportParser,
                                            EventService eventService,
                                            PayoutEmitterService payoutEmitterService) {
        this.adyenPayoutReportParser = adyenPayoutReportParser;
        adyenPaymentProvider = (AdyenPaymentProvider) paymentProviders.byName(PaymentGatewayName.ADYEN);
        this.eventService = eventService;
        this.payoutEmitterService = payoutEmitterService;
    }

    public boolean reconcile(PayoutReconcileMessage payoutReconcileMessage) {

        AdyenPayoutReconciliationPayload payout = (AdyenPayoutReconciliationPayload) payoutReconcileMessage.getPayout();
        try {
            LOGGER.atInfo()
                    .setMessage("Started reconciling Adyen balance platform payout report")
                    .addKeyValue("file_name", payout.getFileName())
                    .addKeyValue("report_type", payout.getReportType())
                    .addKeyValue("report_date", payout.getCreationDate())
                    .addKeyValue("environment", payout.getEnvironment())
                    .log();

            String csv = adyenPaymentProvider.downloadReport(payout);

            var balancePayoutReportRecords = adyenPayoutReportParser.parse(csv);

            reconcileRecords(balancePayoutReportRecords, OffsetDateTime.parse(payout.getCreationDate()).toInstant());
        } catch (Exception e) {
            LOGGER.atError()
                    .setMessage("Failed to reconcile Adyen balance platform payout report")
                    .setCause(e)
                    .log();
            return false;
        }
        return true;
    }

    private void reconcileRecords(List<AdyenBalancePayoutReportRecord> balancePayoutReportRecords, Instant reportDateAndTime) {
        Map<String, AdyenBalancePayoutReportRecord> bankTransferRecordByBalanceAccountId = balancePayoutReportRecords.stream()
                .filter(r -> "bankTransfer".equals(r.type()))
                .collect(Collectors.toMap(AdyenBalancePayoutReportRecord::balanceAccount,
                        adyenBalancePayoutReportRecord -> adyenBalancePayoutReportRecord,
                        (record1, _) -> record1));

        var emittedEventsForIDs = new HashSet<String>();
        balancePayoutReportRecords.forEach(transferRecord -> {

            var bankTransferRecord = bankTransferRecordByBalanceAccountId.get(transferRecord.balanceAccount());
            if (bankTransferRecord == null) {
               LOGGER.atError().setMessage("Payout record is not found for a balance account")
                       .addKeyValue(BALANCE_ACCOUNT_ID, transferRecord.balanceAccount())
                       .addKeyValue(CATEGORY, transferRecord.category())
                       .addKeyValue(TRANSACTION_ID, transferRecord.transactionId())
                       .log();
                return;
            }

            switch (transferRecord.type()) {
                case "capture":
                    if (emittedEventsForIDs.add(transferRecord.pspPaymentMerchantReference())) {
                        emitPaymentEvent(transferRecord, bankTransferRecord);
                    }
                    break;
                case "refund":
                    var pspModificationMerchantReference = transferRecord.pspModificationMerchantReference();
                    if (emittedEventsForIDs.add(pspModificationMerchantReference)) {
                        emitRefundEvent(pspModificationMerchantReference, bankTransferRecord);
                    }
                    break;
                case "bankTransfer":
                    if (emittedEventsForIDs.add(transferRecord.transferId()) && transferRecord.category().equals("bank")) {
                        emitPayoutEvent(transferRecord, bankTransferRecord, reportDateAndTime);
                    }
                    break;
                case null:
                    LOGGER.atError()
                            .setMessage("Payout contains type null, which is unexpected.")
                            .addKeyValue(BALANCE_ACCOUNT_ID, transferRecord.balanceAccount())
                            .addKeyValue(CATEGORY, transferRecord.category())
                            .addKeyValue(TRANSACTION_ID, transferRecord.transactionId())
                            .log();
                    break;
                default:
                    LOGGER.atInfo().
                            setMessage("Ignoring unexpected Payout type")
                            .addKeyValue("type", transferRecord.type())
                            .addKeyValue(BALANCE_ACCOUNT_ID, transferRecord.balanceAccount())
                            .addKeyValue(CATEGORY, transferRecord.category())
                            .addKeyValue(TRANSACTION_ID, transferRecord.transactionId())
                            .log();
                    break;
            }
        });
        LOGGER.atInfo()
                .setMessage("Finished reconciling Adyen balance platform payout report")
                .log();
    }

    private void emitPayoutEvent(AdyenBalancePayoutReportRecord payoutRecord, AdyenBalancePayoutReportRecord bankTransferRecord, Instant timestamp) {
        var payoutEvent = PayoutPaid.from(timestamp, bankTransferRecord);
        
        payoutEmitterService.emitPayoutEvent(payoutEvent, payoutRecord.balanceAccount());

        LOGGER.atInfo()
                .setMessage("Emitted event for payout {}")
                .addArgument(payoutRecord.transferId())
                .log();
    }

    private void emitPaymentEvent(AdyenBalancePayoutReportRecord payoutRecord, AdyenBalancePayoutReportRecord bankTransferRecord) {
        var paymentEvent = new PaymentIncludedInPayout(payoutRecord.pspPaymentMerchantReference(),
                bankTransferRecord.transferId(),
                bankTransferRecord.payoutDate());
        emitEvent(paymentEvent, payoutRecord.pspPaymentMerchantReference(), payoutRecord.transferId());

        LOGGER.atInfo()
                .setMessage("Emitted event for payment {} included in payout {}")
                .addArgument(payoutRecord.pspPaymentMerchantReference())
                .addArgument(payoutRecord.transferId())
                .addKeyValue(PAYMENT_EXTERNAL_ID, bankTransferRecord.pspPaymentMerchantReference())
                .log();
    }

    private void emitRefundEvent(String pspModificationReference, AdyenBalancePayoutReportRecord bankTransfer){
        var transferId = bankTransfer.transferId();
        var refundEvent = new RefundIncludedInPayout(pspModificationReference,
                transferId,
                bankTransfer.payoutDate());
        emitEvent(refundEvent, pspModificationReference, transferId);

        LOGGER.atInfo()
                .setMessage("Emitted event for refund {} included in payout {}")
                .addArgument(pspModificationReference)
                .addArgument(transferId)
                .addKeyValue(REFUND_EXTERNAL_ID, pspModificationReference).log();
    }

    private void emitEvent(Event event, String transactionExternalId, String payoutTransferId) {
        try {
            eventService.emitEvent(event, false);
        } catch (QueueException e) {
            throw new RuntimeException(format("Error sending %s event for transaction [%s] included in payout [%s] to event queue: %s",
                    event.getEventType(), transactionExternalId, payoutTransferId, e.getMessage()), e);
        }
    }
}
