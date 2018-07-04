package uk.gov.pay.connector.cqrs;

import uk.gov.pay.connector.dao.RefundedEventDao;
import uk.gov.pay.connector.dao.SuccessfulChargeEventDao;
import uk.gov.pay.connector.model.TransactionsSummaryResponse;

import javax.inject.Inject;
import java.util.List;

public class TransactionsSummaryQuery {

    private final SuccessfulChargeEventDao successfulChargeEventDao;
    private final RefundedEventDao refundedEventDao;

    @Inject
    public TransactionsSummaryQuery(SuccessfulChargeEventDao successfulChargeEventDao, RefundedEventDao refundedEventDao) {
        this.successfulChargeEventDao = successfulChargeEventDao;
        this.refundedEventDao = refundedEventDao;
    }

    public TransactionsSummaryResponse execute(Long gatewayAccountId, String fromDate, String toDate) {
        List<SuccessfulChargeEvent> successfulPayments = successfulChargeEventDao.find(gatewayAccountId, fromDate, toDate);
        long paymentsValue = successfulPayments.stream().mapToLong(e -> e.getAmount()).sum();
        List<RefundedEvent> refundedEvents = refundedEventDao.find(gatewayAccountId, fromDate, toDate);
        long refundsValue = refundedEvents.stream().mapToLong(e -> e.getAmount()).sum();
        long netIncome = paymentsValue - refundsValue;
        return new TransactionsSummaryResponse(successfulPayments.size(), paymentsValue, refundedEvents.size(), refundsValue, netIncome);
    }
}
