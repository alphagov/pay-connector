package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.charge.model.ServicePaymentReference;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

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
