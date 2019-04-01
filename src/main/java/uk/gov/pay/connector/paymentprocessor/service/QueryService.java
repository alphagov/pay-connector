package uk.gov.pay.connector.paymentprocessor.service;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;

import javax.inject.Inject;

public class QueryService {
    private final PaymentProviders providers;

    @Inject
    public QueryService(PaymentProviders providers) {
        this.providers = providers;
    }
    
    public ChargeQueryResponse getChargeGatewayStatus(ChargeEntity charge) throws GatewayException {
        return providers.byName(charge.getPaymentGatewayName()).queryPaymentStatus(charge);
    }
}
