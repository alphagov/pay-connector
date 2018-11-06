package uk.gov.pay.connector.gateway.handler;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviderFactory;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import javax.inject.Inject;

public class GatewayHandler {
    private final PaymentProviderFactory paymentProviderFactory;

    @Inject
    public GatewayHandler(PaymentProviderFactory paymentProviderFactory) {
        this.paymentProviderFactory = paymentProviderFactory;
    }

    public AuthorisationHandler<BaseAuthoriseResponse> getAuthorisationHandler(PaymentGatewayName gateway) {
        return (AuthorisationHandler<BaseAuthoriseResponse>) getPaymentProviderByName(gateway);
    }

    public AuthorisationHandler<BaseAuthoriseResponse> getCaptureHandler(PaymentGatewayName gateway) {
        return (AuthorisationHandler<BaseAuthoriseResponse>) getPaymentProviderByName(gateway);
    }

    private PaymentProvider getPaymentProviderByName(PaymentGatewayName gateway) {
        return paymentProviderFactory.byName(gateway);
    }
}
