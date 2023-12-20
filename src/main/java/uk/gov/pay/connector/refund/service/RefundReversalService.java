package uk.gov.pay.connector.refund.service;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.Refund;

import javax.inject.Inject;
import java.util.Optional;

public class RefundReversalService {
    private final LedgerService ledgerService;
    private final RefundDao refundDao;

    @Inject
    public RefundReversalService(LedgerService ledgerService, RefundDao refundDao) {
            this.ledgerService = ledgerService;
            this.refundDao = refundDao;
    }

    public Optional<Refund> findMaybeHistoricRefundByRefundId(String refundExternalId) {
           return refundDao.findByExternalId(refundExternalId)
                   .map(Refund::from)
                   .or(() -> ledgerService.getTransaction(refundExternalId).map(Refund::from));
    }
}
