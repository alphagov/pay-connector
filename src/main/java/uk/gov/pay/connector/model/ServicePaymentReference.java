package uk.gov.pay.connector.model;

import java.util.Objects;

public class ServicePaymentReference {

    private final String servicePaymentReference;

    private ServicePaymentReference(String servicePaymentReference) {
        this.servicePaymentReference = Objects.requireNonNull(servicePaymentReference);
    }

    public static ServicePaymentReference of(String providerMandateReference) {
        return new ServicePaymentReference(providerMandateReference);
    }

    @Override
    public boolean equals(Object other) {
        if (other != null && other.getClass() == ServicePaymentReference.class) {
            ServicePaymentReference that = (ServicePaymentReference) other;
            return this.servicePaymentReference.equals(that.servicePaymentReference);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return servicePaymentReference.hashCode();
    }

    @Override
    public String toString() {
        return servicePaymentReference;
    }

}
