package uk.gov.pay.connector.tasks.service;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.tasks.HistoricalEventEmitter;

import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;

public class ParityCheckService {

    public static final String FIELD_NAME = "field_name";
    private static final Logger logger = LoggerFactory.getLogger(ParityCheckService.class);
    private LedgerService ledgerService;
    private ChargeService chargeService;
    private RefundService refundService;
    private HistoricalEventEmitter historicalEventEmitter;
    private final ChargeParityChecker chargeParityChecker;
    private final RefundParityChecker refundParityChecker;

    @Inject
    public ParityCheckService(LedgerService ledgerService,
                              ChargeService chargeService,
                              HistoricalEventEmitter historicalEventEmitter,
                              ChargeParityChecker chargeParityChecker, RefundParityChecker refundParityChecker,
                              RefundService refundService) {
        this.ledgerService = ledgerService;
        this.chargeService = chargeService;
        this.refundService = refundService;
        this.historicalEventEmitter = historicalEventEmitter;
        this.chargeParityChecker = chargeParityChecker;
        this.refundParityChecker = refundParityChecker;
    }

    public ParityCheckStatus getChargeAndRefundsParityCheckStatus(ChargeEntity charge) {
        ParityCheckStatus parityCheckStatus = getChargeParityCheckStatus(charge);
        if (parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
            return getRefundsParityCheckStatus(refundService.findNotExpungedRefunds(charge.getExternalId()));
        }

        return parityCheckStatus;
    }

    @Transactional
    public boolean parityCheckChargeForExpunger(ChargeEntity chargeEntity) {
        ParityCheckStatus parityCheckStatus = getChargeParityCheckStatus(chargeEntity);

        if (EXISTS_IN_LEDGER.equals(parityCheckStatus)) {
            return true;
        }

        // force emit and update charge status
        historicalEventEmitter.processPaymentEvents(chargeEntity, true);
        chargeService.updateChargeParityStatus(chargeEntity.getExternalId(), parityCheckStatus);

        return false;
    }

    @Transactional
    public boolean parityCheckRefundForExpunger(RefundEntity refundEntity) {
        ParityCheckStatus parityCheckStatus = getRefundParityCheckStatus(refundEntity);

        if (EXISTS_IN_LEDGER.equals(parityCheckStatus)) {
            return true;
        }

        historicalEventEmitter.emitEventsForRefund(refundEntity.getExternalId(), true);
        refundService.updateRefundParityStatus(refundEntity.getExternalId(), parityCheckStatus);

        return false;
    }

    private ParityCheckStatus getRefundsParityCheckStatus(List<RefundEntity> refunds) {
        for (var refund : refunds) {
            ParityCheckStatus parityCheckStatus = getRefundParityCheckStatus(refund);
            if (!parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
                logger.info("refund transaction does not exist in ledger or is in a different state [externalId={},status={}] -",
                        refund.getExternalId(), parityCheckStatus);
                return parityCheckStatus;
            }
        }

        return EXISTS_IN_LEDGER;
    }

    public ParityCheckStatus getRefundParityCheckStatus(RefundEntity refundEntity) {
        Optional<LedgerTransaction> transaction = ledgerService.getTransaction(refundEntity.getExternalId());
        return refundParityChecker.checkParity(refundEntity, transaction.orElse(null));
    }

    private ParityCheckStatus getChargeParityCheckStatus(ChargeEntity chargeEntity) {
        Optional<LedgerTransaction> transaction = ledgerService.getTransaction(chargeEntity.getExternalId());
        return chargeParityChecker.checkParity(chargeEntity, transaction.orElse(null));
    }
}
