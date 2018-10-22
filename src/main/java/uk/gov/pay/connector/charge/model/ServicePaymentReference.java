package uk.gov.pay.connector.charge.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class ServicePaymentReference {

    private final String servicePaymentReference;

    private ServicePaymentReference(String servicePaymentReference) {
        this.servicePaymentReference = Objects.requireNonNull(servicePaymentReference);
    }

    public static ServicePaymentReference ofNullable(String servicePaymentReference) {
        if (isBlank(servicePaymentReference)) {
            return null;
        }
        return new ServicePaymentReference(servicePaymentReference);
    }
    
    public static ServicePaymentReference of(String servicePaymentReference) {
        return new ServicePaymentReference(servicePaymentReference);
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
