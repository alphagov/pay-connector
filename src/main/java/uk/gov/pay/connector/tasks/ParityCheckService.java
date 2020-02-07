package uk.gov.pay.connector.tasks;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.paritycheck.LedgerService;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;

public class ParityCheckService {

    private static final Logger logger = LoggerFactory.getLogger(ParityCheckService.class);

    private LedgerService ledgerService;
    private ChargeService chargeService;
    private RefundDao refundDao;
    private HistoricalEventEmitter historicalEventEmitter;

    @Inject
    public ParityCheckService(LedgerService ledgerService, ChargeService chargeService,
                              RefundDao refundDao,
                              HistoricalEventEmitter historicalEventEmitter) {
        this.ledgerService = ledgerService;
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.historicalEventEmitter = historicalEventEmitter;
    }

    public ParityCheckStatus getChargeParityCheckStatus(ChargeEntity charge) {
        Optional<LedgerTransaction> transaction = ledgerService.getTransaction(charge.getExternalId());
        var externalChargeState = ChargeStatus.fromString(charge.getStatus()).toExternal().getStatusV2();

        return getParityCheckStatus(transaction, externalChargeState);
    }

    public ParityCheckStatus getChargeAndRefundsParityCheckStatus(ChargeEntity charge) {
        var parityCheckStatus = getChargeParityCheckStatus(charge);
        if (parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
            return getRefundsParityCheckStatus(refundDao.findRefundsByChargeExternalId(charge.getExternalId()));
        }

        return parityCheckStatus;
    }

    public ParityCheckStatus getParityCheckStatus(Optional<LedgerTransaction> transaction, String externalChargeState) {
        if (transaction.isEmpty()) {
            return ParityCheckStatus.MISSING_IN_LEDGER;
        }

        if (externalChargeState.equalsIgnoreCase(transaction.get().getState().getStatus())) {
            return EXISTS_IN_LEDGER;
        }

        return ParityCheckStatus.DATA_MISMATCH;
    }


    public ParityCheckStatus getRefundsParityCheckStatus(List<RefundEntity> refunds) {
        for (var refund : refunds) {
            var transaction = ledgerService.getTransaction(refund.getExternalId());
            ParityCheckStatus parityCheckStatus = getParityCheckStatus(transaction, refund.getStatus().toExternal().getStatus());
            if (!parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
                logger.info("refund transaction does not exist in ledger or is in a different state [externalId={},status={}] -",
                        refund.getExternalId(), parityCheckStatus);
                return parityCheckStatus;
            }
        }

        return EXISTS_IN_LEDGER;
    }

    @Transactional
    public boolean parityCheckChargeForExpunger(ChargeEntity chargeEntity) {
        ParityCheckStatus parityCheckStatus = getChargeParityCheckStatus(chargeEntity);

        //TODO (kbottla) to be replaced by `MATCHES_WITH_LEDGER`          
        if (EXISTS_IN_LEDGER.equals(parityCheckStatus)) {
            return true;
        }

        // force emit and update charge status
        historicalEventEmitter.processPaymentEvents(chargeEntity, true);
        chargeService.updateChargeParityStatus(chargeEntity.getExternalId(), parityCheckStatus);

        return false;
    }
}
