package uk.gov.pay.connector.paymentinstrument.service;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.exception.PaymentInstrumentNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.charge.PaymentInstrumentCreated;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.InstantSource;
import java.util.Map;

public class PaymentInstrumentService {

    private final PaymentInstrumentDao paymentInstrumentDao;
    private final LedgerService ledgerService;
    private final InstantSource instantSource;
    
    @Inject
    public PaymentInstrumentService(PaymentInstrumentDao paymentInstrumentDao, LedgerService ledgerService, InstantSource instantSource) {
        this.paymentInstrumentDao = paymentInstrumentDao;
        this.ledgerService = ledgerService;
        this.instantSource = instantSource;
    }

    public PaymentInstrumentEntity findByExternalId(String externalId) {
        return paymentInstrumentDao.findByExternalId(externalId).orElseThrow(() -> new PaymentInstrumentNotFoundRuntimeException(externalId));
    }
    
    @Transactional
    public PaymentInstrumentEntity createPaymentInstrument(ChargeEntity charge, Map<String, String> recurringAuthToken) {
        var now = instantSource.instant();
        var paymentInstrument = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                .withCreatedDate(now)
                .withRecurringAuthToken(recurringAuthToken)
                .withCardDetails(charge.getCardDetails())
                .withStatus(PaymentInstrumentStatus.CREATED) 
                .withStartDate(now)
                .build();
        paymentInstrumentDao.persist(paymentInstrument);
        ledgerService.postEvent(PaymentInstrumentCreated.from(paymentInstrument, charge.getGatewayAccount()));
        return paymentInstrument;
    }
}
