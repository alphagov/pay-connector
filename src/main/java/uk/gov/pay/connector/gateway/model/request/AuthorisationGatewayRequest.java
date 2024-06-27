package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.util.Optional;

public interface AuthorisationGatewayRequest extends GatewayRequest {
    public String email();
    public boolean isMoto();
    public SupportedLanguage language();
    public Optional<String> getTransactionId();
    public String amount();
    public String description();
    public ServicePaymentReference reference();
    public String govUkPayPaymentId();
    public boolean isSavePaymentInstrumentToAgreement();
    public Optional<AgreementEntity> agreement();
}
