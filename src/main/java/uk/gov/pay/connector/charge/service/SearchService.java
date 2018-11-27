package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.dao.TransactionDao;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.common.service.search.SearchStrategy;

import javax.inject.Inject;

public class SearchService {

    public enum TYPE {CHARGE, TRANSACTION}

    private ChargeDao chargeDao;
    private TransactionDao transactionDao;
    private ChargeService chargeService;
    private CorporateCardSurchargeCalculator corporateCardSurchargeCalculator;

    @Inject
    public SearchService(ChargeDao chargeDao, TransactionDao transactionDao, ChargeService chargeService,
                         CorporateCardSurchargeCalculator corporateCardSurchargeCalculator) {
        this.chargeDao = chargeDao;
        this.transactionDao=transactionDao;
        this.chargeService = chargeService;
        this.corporateCardSurchargeCalculator = corporateCardSurchargeCalculator;
    }

    public SearchStrategy ofType(TYPE type) {
        switch (type) {
            case TRANSACTION:
                return new TransactionSearchStrategy(transactionDao, corporateCardSurchargeCalculator);
            default:
                return new ChargeSearchStrategy(chargeService, chargeDao);
        }
    }
}
