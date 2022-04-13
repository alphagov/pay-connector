package uk.gov.pay.connector.paymentinstrument.service;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.Clock;
import java.util.Map;

public class PaymentInstrumentService {

    private final PaymentInstrumentDao paymentInstrumentDao;
    private final Clock clock;
    
    @Inject
    public PaymentInstrumentService(PaymentInstrumentDao paymentInstrumentDao, Clock clock) {
        this.paymentInstrumentDao = paymentInstrumentDao;
        this.clock = clock;
    }

    @Transactional
    public PaymentInstrumentEntity createPaymentInstrument(CardDetailsEntity cardDetails, Map<String, String> recurringAuthToken) {
        var now = clock.instant();
        var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                .withCreatedDate(now)
                .withRecurringAuthToken(recurringAuthToken)
                .withCardDetails(cardDetails)
                .withStatus(PaymentInstrumentStatus.CREATED) 
                .withStartDate(now)
                .build();
        paymentInstrumentDao.persist(paymentInstrument);
        return paymentInstrument;
    }
}
