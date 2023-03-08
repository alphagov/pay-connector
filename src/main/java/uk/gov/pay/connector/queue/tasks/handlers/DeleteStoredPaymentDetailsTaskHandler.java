package uk.gov.pay.connector.queue.tasks.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;

import javax.inject.Inject;

public class DeleteStoredPaymentDetailsTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteStoredPaymentDetailsTaskHandler.class);
    private AgreementService agreementService;
    private PaymentInstrumentService paymentInstrumentService;
    private PaymentProviders providers;

    @Inject
    public DeleteStoredPaymentDetailsTaskHandler(AgreementService agreementService, PaymentInstrumentService paymentInstrumentService, PaymentProviders providers) {
        this.agreementService = agreementService;
        this.paymentInstrumentService = paymentInstrumentService;
        this.providers = providers;
    }

    public void process(String agreementExternalId, String paymentInstrumentExternalId) throws GatewayException {
        var agreement = agreementService.findByExternalId(agreementExternalId);
        var paymentInstrument = paymentInstrumentService.findByExternalId(paymentInstrumentExternalId);
        PaymentProvider paymentProvider = providers.byName(PaymentGatewayName.valueFrom(agreement.getGatewayAccount().getGatewayName()));
        DeleteStoredPaymentDetailsGatewayRequest request = new DeleteStoredPaymentDetailsGatewayRequest(agreement, paymentInstrument);
        paymentProvider.deleteStoredPaymentDetails(request);
    }
}
