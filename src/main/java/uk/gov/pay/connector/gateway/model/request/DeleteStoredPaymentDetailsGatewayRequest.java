package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Map;

public class DeleteStoredPaymentDetailsGatewayRequest implements GatewayRequest{
    private PaymentInstrumentEntity paymentInstrument;
    private AgreementEntity agreement;

    public DeleteStoredPaymentDetailsGatewayRequest(AgreementEntity agreement, PaymentInstrumentEntity paymentInstrument) {
        this.agreement = agreement;
        this.paymentInstrument = paymentInstrument;
    }

    public AgreementEntity getAgreement() {
        return agreement;
    }

    public PaymentInstrumentEntity getPaymentInstrument() {
        return paymentInstrument;
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return agreement.getGatewayAccount();
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.DELETE_STORED_PAYMENT_DETAILS;
    }

    @Override
    public Map<String, Object> getGatewayCredentials() {
        return agreement.getGatewayAccount().getCredentials(agreement.getGatewayAccount().getGatewayName());
    }

    @Override
    public AuthorisationMode getAuthorisationMode() {
        return AuthorisationMode.AGREEMENT;
    }
}
