package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import javax.inject.Inject;
import java.util.Map;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;

public class AuthorisationProviders {
    private final Map<PaymentGatewayName, AuthorisationProvider> authorisationProviders = newHashMap();

    @Inject
    public AuthorisationProviders(WorldpayPaymentProvider worldpayPaymentProvider,
                            EpdqPaymentProvider epdqPaymentProvider,
                            SmartpayPaymentProvider smartpayPaymentProvider,
                            SandboxPaymentProvider sandboxPaymentProvider) {
        this.authorisationProviders.put(PaymentGatewayName.WORLDPAY, worldpayPaymentProvider);
        this.authorisationProviders.put(PaymentGatewayName.SMARTPAY, smartpayPaymentProvider);
        this.authorisationProviders.put(PaymentGatewayName.SANDBOX, sandboxPaymentProvider);
        this.authorisationProviders.put(PaymentGatewayName.EPDQ, epdqPaymentProvider);
    }

    public AuthorisationProvider<BaseAuthoriseResponse> byName(PaymentGatewayName gateway) {
        return authorisationProviders.get(gateway);
    }
}
