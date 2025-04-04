package uk.gov.pay.connector.charge.model.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class FeeTypeConverter implements AttributeConverter<FeeType, String> {
    @Override
    public String convertToDatabaseColumn(FeeType feeType) {
        return feeType == null ? null : feeType.getName();
    }

    @Override
    public FeeType convertToEntityAttribute(String feeType) {
        return feeType == null ? null : FeeType.fromString(feeType);
    }
}
