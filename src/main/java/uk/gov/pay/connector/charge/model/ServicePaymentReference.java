package uk.gov.pay.connector.charge.model;

import uk.gov.pay.commons.model.WrappedStringValue;

public class ServicePaymentReference extends WrappedStringValue {

    private ServicePaymentReference(String servicePaymentReference) {
        super(servicePaymentReference);
    }

    public static ServicePaymentReference of(String servicePaymentReference) {
        return new ServicePaymentReference(servicePaymentReference);
    }

}
