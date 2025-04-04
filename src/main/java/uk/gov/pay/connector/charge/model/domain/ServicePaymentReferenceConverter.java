package uk.gov.pay.connector.charge.model.domain;

import uk.gov.pay.connector.charge.model.ServicePaymentReference;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ServicePaymentReferenceConverter implements AttributeConverter<ServicePaymentReference, String> {

    @Override
    public String convertToDatabaseColumn(ServicePaymentReference serviceProviderReference) {
        return serviceProviderReference.toString();
    }

    @Override
    public ServicePaymentReference convertToEntityAttribute(String serviceProviderReference) {
        return ServicePaymentReference.of(serviceProviderReference);
    }

}
