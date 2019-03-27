package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.stripe.fee.StripeFeeProcessor;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;

public class FeeProcessors {
    private final Map<PaymentGatewayName, FeeProcessor> feeProcessors = newHashMap();


    @Inject
    FeeProcessors(StripeFeeProcessor stripeFeeProcessor) {
        feeProcessors.put(PaymentGatewayName.STRIPE, stripeFeeProcessor);
    }
    
    public FeeProcessor byPaymentGatewayName(PaymentGatewayName gateway) {
        return feeProcessors.get(gateway);
    }
}
