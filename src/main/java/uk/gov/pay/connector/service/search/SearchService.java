package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.TransactionDao;
import uk.gov.pay.connector.service.ChargeService;

import javax.inject.Inject;

public class SearchService {

    public enum TYPE {CHARGE, TRANSACTION}

    private ChargeDao chargeDao;
    private TransactionDao transactionDao;
    private ChargeService chargeService;

    @Inject
    public SearchService(ChargeDao chargeDao, TransactionDao transactionDao, ChargeService chargeService) {
        this.chargeDao = chargeDao;
        this.transactionDao = transactionDao;
        this.chargeService = chargeService;
    }

    //to return instance, it is thread safe!
    public SearchStrategy ofType(TYPE type) {
        switch (type) {
            case TRANSACTION:
                return new TransactionSearchStrategy(transactionDao);
            default:
                return new ChargeSearchStrategy(chargeService, chargeDao);
        }
    }
}
