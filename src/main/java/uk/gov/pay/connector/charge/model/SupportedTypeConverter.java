package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.cardtype.model.domain.SupportedType;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class SupportedTypeConverter implements AttributeConverter<SupportedType, String> {

    @Override
    public String convertToDatabaseColumn(SupportedType supportedType) {
        if(supportedType != null) {
            return supportedType.toString();
        } else {
            return null;
        }
    }

    @Override
    public SupportedType convertToEntityAttribute(String s) {
        if(s != null) {
            return SupportedType.valueOf(s);
        } else {
            return null;
        }
    }
}
