package uk.gov.pay.connector.queue.tasks.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;

import javax.inject.Inject;

public class DeleteStoredPaymentDetailsHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteStoredPaymentDetailsHandler.class);
    private AgreementService agreementService;
    private PaymentInstrumentService paymentInstrumentService;
    
    @Inject
    public DeleteStoredPaymentDetailsHandler(AgreementService agreementService, PaymentInstrumentService paymentInstrumentService) {
        this.agreementService = agreementService;
        this.paymentInstrumentService = paymentInstrumentService;
    }

    public void process(String agreementId, String paymentInstrumentId) {
        var agreement = agreementService.findByExternalId(agreementId);
        var paymentInstrument = paymentInstrumentService.findByExternalId(paymentInstrumentId);
        //TODO: replace logging statement with request to Payment Processor to delete stored payment details
        LOGGER.info("Processed deleteStoredPaymentDetails task for agreement [{}] and payment instrument [{}].", 
                agreement.getExternalId(),
                paymentInstrument.getExternalId());
    }
}
