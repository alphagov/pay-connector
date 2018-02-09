package uk.gov.pay.connector.service.search;

import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.OldTransactionDao;
import uk.gov.pay.connector.service.ChargeService;

import javax.inject.Inject;

public class SearchService {

    public enum TYPE {CHARGE, TRANSACTION}

    private ChargeDao chargeDao;
    private OldTransactionDao oldTransactionDao;
    private ChargeService chargeService;
    private final CardTypeDao cardTypeDao;

    @Inject
    public SearchService(ChargeDao chargeDao, OldTransactionDao oldTransactionDao, ChargeService chargeService, CardTypeDao cardTypeDao) {
        this.chargeDao = chargeDao;
        this.oldTransactionDao = oldTransactionDao;
        this.chargeService = chargeService;
        this.cardTypeDao = cardTypeDao;
    }

    public SearchStrategy ofType(TYPE type) {
        switch (type) {
            case TRANSACTION:
                return new OldTransactionSearchStrategy(oldTransactionDao, cardTypeDao);
            default:
                return new ChargeSearchStrategy(chargeService, chargeDao, cardTypeDao);
        }
    }
}
